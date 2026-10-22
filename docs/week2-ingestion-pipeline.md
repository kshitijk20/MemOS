# Week 2 — Ingestion Pipeline: Parsing, Chunking, Embeddings, Vector Storage

Reference dump of the MemOS ingestion pipeline — from HTTP upload to searchable pgvector chunks.
Structured to answer the interview questions at the bottom.

## Contents

1. [Async pipeline mechanics](#1-async-pipeline-mechanics)
2. [Document parser port & registry (strategy pattern)](#2-document-parser-port--registry)
3. [PDF parser](#3-pdf-parser)
4. [Markdown parser](#4-markdown-parser)
5. [Git repository parser](#5-git-repository-parser)
6. [Chunking](#6-chunking)
7. [Embeddings — rate-limited batch adapter](#7-embeddings)
8. [Vector storage & retrieval](#8-vector-storage--retrieval)
9. [Redis embedding cache](#9-redis-embedding-cache)
10. [Flyway migrations (V3, V4, V8)](#10-flyway-migrations)
11. [End-to-end flow summary](#11-end-to-end-flow-summary)
12. [Interview Q&A](#12-interview-qa)

---

## 1. Async pipeline mechanics

### DocumentIngestionService

`src/main/java/com/memos/application/ingestion/DocumentIngestionService.java`

```java
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentIngestionService {

    private final DocumentRepositoryPort documentRepositoryPort;
    private final DocumentChunkRepositoryPort documentChunkRepositoryPort;
    private final DocumentParserRegistry parserRegistry;
    private final ChunkingPort chunkingPort;
    private final EmbeddingPort embeddingPort;

    public Document submit(UUID ownerId, String title, DocumentSourceType sourceType,
                           String sourceUri, Map<String, Object> metadata) {
        Instant now = Instant.now();
        Document document = Document.builder()
                .ownerId(ownerId)
                .title(title)
                .sourceType(sourceType)
                .sourceUri(sourceUri)
                .status(DocumentStatus.PENDING)
                .metadata(metadata)
                .createdAt(now)
                .updatedAt(now)
                .build();
        return documentRepositoryPort.save(document);
    }

    @Async
    public void ingest(UUID documentId) {
        Document document = documentRepositoryPort.findById(documentId).orElse(null);
        if (document == null) {
            log.warn("Document {} no longer exists, skipping ingestion", documentId);
            return;
        }

        try {
            document = document.withStatus(DocumentStatus.PROCESSING);
            documentRepositoryPort.save(document);

            DocumentParserPort parser = parserRegistry.resolve(document.getSourceType());
            List<ParsedDocument> parsedDocuments = parser.parse(document);

            List<DocumentChunk> chunksToSave = buildChunks(document, parsedDocuments);
            documentChunkRepositoryPort.saveAll(chunksToSave);

            document = document.withStatus(DocumentStatus.COMPLETED);
            documentRepositoryPort.save(document);
            log.info("Ingestion completed for document {} ({} chunks)", documentId, chunksToSave.size());
        } catch (Exception e) {
            log.error("Ingestion failed for document {}", documentId, e);
            documentRepositoryPort.save(document.withFailure(e.getMessage()));
        } finally {
            deleteTempSourceFile(document);  // cleans up /tmp/memos-upload-* for PDF/Markdown
        }
    }

    private List<DocumentChunk> buildChunks(Document document, List<ParsedDocument> parsedDocuments) {
        List<DocumentChunk> chunksToSave = new ArrayList<>();
        int globalIndex = 0;

        for (ParsedDocument parsed : parsedDocuments) {
            List<TextChunk> textChunks = chunkingPort.chunk(parsed.content());
            if (textChunks.isEmpty()) continue;

            // embed the whole batch in one (paced) call
            List<float[]> embeddings = embeddingPort.embedBatch(
                    textChunks.stream().map(TextChunk::content).toList());

            for (int i = 0; i < textChunks.size(); i++) {
                TextChunk textChunk = textChunks.get(i);
                Map<String, Object> mergedMetadata = new HashMap<>();
                mergedMetadata.put("sourceLabel", parsed.sourceLabel());
                mergedMetadata.putAll(parsed.metadata());
                mergedMetadata.putAll(textChunk.metadata());

                chunksToSave.add(DocumentChunk.builder()
                        .documentId(document.getId())
                        .chunkIndex(globalIndex++)
                        .content(textChunk.content())
                        .embedding(embeddings.get(i))
                        .tokenCount(textChunk.tokenCount())
                        .metadata(mergedMetadata)
                        .createdAt(Instant.now())
                        .build());
            }
        }
        return chunksToSave;
    }
}
```

**Key points:**

- `submit()` (synchronous) — creates the `documents` row with `status=PENDING` and returns immediately. The controller gets a response before any heavy work starts.
- `ingest()` (`@Async`) — runs on a Spring-managed thread pool. Because the controller calls `documentIngestionService.ingest(...)` on an injected proxy (a different bean), Spring's AOP intercepts the call and dispatches it asynchronously. If you called `this.ingest(...)` from within the same class, the proxy would be bypassed and it would run synchronously.
- Thread pool: `spring.threads.virtual.enabled=true` is set in `application.yml`, so Spring Boot uses a virtual-thread executor. Each async invocation gets its own virtual thread — no fixed pool size to tune, no starvation under concurrent uploads.
- Status transitions: `PENDING → PROCESSING` (at start of `ingest`) → `COMPLETED` (on success) or `FAILED` (on any exception). The `error_message` column is populated on failure.
- Temp file cleanup: uploaded PDF/Markdown files land in `/tmp/memos-upload-*`. The `finally` block deletes the file after parsing (whether the parse succeeded or threw). Git clones self-clean inside `GitRepositoryParser`.

### AsyncConfig

`src/main/java/com/memos/infrastructure/config/AsyncConfig.java`

```java
@Slf4j
@Configuration
public class AsyncConfig implements AsyncConfigurer {

    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return (Throwable ex, Method method, Object... params) ->
                log.error("Unhandled exception in async method '{}'", method.getName(), ex);
    }
}
```

`@Async void` methods cannot propagate exceptions to the caller. Without `AsyncUncaughtExceptionHandler`, failures would silently disappear. This handler logs them. Note: `ingest()` already catches its own exceptions and updates the document to `FAILED`, so this handler only fires for truly unexpected runtime errors (e.g. out of memory).

---

## 2. Document parser port & registry

`src/main/java/com/memos/application/ingestion/DocumentParserPort.java`

```java
public interface DocumentParserPort {
    DocumentSourceType sourceType();
    List<ParsedDocument> parse(Document document);
}
```

`src/main/java/com/memos/infrastructure/ingestion/parser/DocumentParserRegistry.java`

```java
@Component
public class DocumentParserRegistry {

    private final Map<DocumentSourceType, DocumentParserPort> parsersBySourceType;

    public DocumentParserRegistry(List<DocumentParserPort> parsers) {
        this.parsersBySourceType = parsers.stream()
                .collect(Collectors.toMap(DocumentParserPort::sourceType, Function.identity()));
    }

    public DocumentParserPort resolve(DocumentSourceType sourceType) {
        DocumentParserPort parser = parsersBySourceType.get(sourceType);
        if (parser == null) {
            throw new IngestionException("No parser registered for source type: " + sourceType);
        }
        return parser;
    }
}
```

**Strategy pattern.** Spring injects all `DocumentParserPort` beans into the registry at startup. Adding a new source (e.g. Confluence) requires only:
1. Adding a value to `DocumentSourceType`
2. Implementing `DocumentParserPort` (annotated `@Component`)
3. No changes to `DocumentIngestionService` or `DocumentParserRegistry`

`ParsedDocument` — the common output type:

```java
public record ParsedDocument(
    String sourceLabel,   // human-readable label for the source (title or file path)
    String content,       // plain text content to be chunked
    Map<String, Object> metadata  // source-specific extras stored with chunks (page count, file path, etc.)
) {}
```

---

## 3. PDF parser

`src/main/java/com/memos/infrastructure/ingestion/parser/PdfDocumentParser.java`

```java
@Slf4j
@Component
public class PdfDocumentParser implements DocumentParserPort {

    @Override
    public DocumentSourceType sourceType() { return DocumentSourceType.PDF; }

    @Override
    public List<ParsedDocument> parse(Document document) {
        Path path = Path.of(document.getSourceUri());
        try (PDDocument pdf = Loader.loadPDF(path.toFile())) {
            String text = new PDFTextStripper().getText(pdf);
            // PDFBox emits NUL (0x00) for glyphs with no Unicode mapping —
            // Postgres text columns reject NUL bytes, so strip them.
            text = text.replace(" ", "");
            text = repairCaesarShift(text);
            Map<String, Object> metadata = Map.of("pageCount", pdf.getNumberOfPages());
            return List.of(new ParsedDocument(document.getTitle(), text, metadata));
        } catch (IOException e) {
            throw new IngestionException("Failed to parse PDF '" + document.getTitle() + "': " + e.getMessage(), e);
        }
    }

    // ... Caesar-shift repair (see below)
}
```

**Library:** Apache PDFBox 3. Reads from the temp file path stored in `document.sourceUri` (written by the upload endpoint).

**NUL-byte fix:** PDFBox emits ` ` for glyphs it can't map to Unicode (broken font subsets). PostgreSQL's `text` type rejects NUL bytes (`ERROR: invalid byte sequence`), so they are stripped before storage.

**Caesar-shift repair:** Some LaTeX-generated PDFs with non-standard subsetted fonts extract as text that's uniformly shifted — e.g. every character is N positions earlier in the alphabet than the correct one (so "The" becomes "Sgd" for shift=1). The repair scans extracted words across all 26 possible forward-shift values, counting how many become common English words. If one non-zero shift is a clear winner (≥3× the match rate of no shift, ≥5% total match rate), the entire document's text is shifted forward by that amount. This is a heuristic, not a guarantee, but handles the most common case.

---

## 4. Markdown parser

`src/main/java/com/memos/infrastructure/ingestion/parser/MarkdownDocumentParser.java`

```java
@Component
public class MarkdownDocumentParser implements DocumentParserPort {

    private final Parser parser = Parser.builder().build();

    @Override
    public DocumentSourceType sourceType() { return DocumentSourceType.MARKDOWN; }

    @Override
    public List<ParsedDocument> parse(Document document) {
        Path path = Path.of(document.getSourceUri());
        try {
            String markdown = Files.readString(path, StandardCharsets.UTF_8);
            Node root = parser.parse(markdown);
            PlainTextVisitor visitor = new PlainTextVisitor();
            root.accept(visitor);
            return List.of(new ParsedDocument(document.getTitle(), visitor.toString(), Map.of()));
        } catch (IOException e) {
            throw new IngestionException("Failed to read markdown file '" + document.getTitle() + "'", e);
        }
    }

    private static final class PlainTextVisitor extends AbstractVisitor {
        private final StringBuilder text = new StringBuilder();

        @Override public void visit(Heading h)           { text.append('\n'); visitChildren(h); text.append("\n\n"); }
        @Override public void visit(Paragraph p)         { visitChildren(p); text.append("\n\n"); }
        @Override public void visit(Text t)              { text.append(t.getLiteral()); }
        @Override public void visit(Code c)              { text.append(c.getLiteral()); }
        @Override public void visit(FencedCodeBlock fcb) { text.append('\n').append(fcb.getLiteral()).append('\n'); }
        @Override public void visit(IndentedCodeBlock i) { text.append('\n').append(i.getLiteral()).append('\n'); }
        @Override public void visit(SoftLineBreak s)     { text.append(' '); }
        @Override public void visit(HardLineBreak h)     { text.append('\n'); }
    }
}
```

**Library:** CommonMark (spec-compliant Markdown parser). Strips all Markdown syntax (bold, italic, links, tables) while preserving semantic structure — headings get `\n\n` padding so they become paragraph boundaries for the chunker. Code blocks are included as plain text. This gives the chunker clean paragraphs rather than Markdown noise.

---

## 5. Git repository parser

`src/main/java/com/memos/infrastructure/ingestion/parser/GitRepositoryParser.java`

```java
@Slf4j
@Component
public class GitRepositoryParser implements DocumentParserPort {

    private final Set<String> allowedExtensions;  // from memos.ingestion.git.allowed-extensions
    private final long maxFileSizeBytes;           // default 1MB
    private final int cloneTimeoutSeconds;         // default 120

    @Override
    public DocumentSourceType sourceType() { return DocumentSourceType.GIT_REPOSITORY; }

    @Override
    public List<ParsedDocument> parse(Document document) {
        Path tempDir = Files.createTempDirectory("memos-git-");
        try {
            CloneCommand cloneCommand = Git.cloneRepository()
                    .setURI(document.getSourceUri())
                    .setDirectory(tempDir.toFile())
                    .setDepth(1)           // shallow clone — only latest commit, no history
                    .setCloneAllBranches(false)
                    .setTimeout(cloneTimeoutSeconds);

            // optional: document.metadata may specify a branch
            Object branch = document.getMetadata().get("branch");
            if (branch instanceof String b && !b.isBlank()) {
                cloneCommand.setBranch(b);
            }

            try (Git git = cloneCommand.call()) {
                log.debug("Cloned {} into {}", document.getSourceUri(), tempDir);
            }
            return walkRepository(tempDir);
        } catch (GitAPIException e) {
            throw new IngestionException("Failed to clone '" + document.getSourceUri() + "': " + e.getMessage(), e);
        } finally {
            deleteRecursively(tempDir);  // always clean up, success or failure
        }
    }

    private List<ParsedDocument> walkRepository(Path tempDir) throws IOException {
        try (Stream<Path> paths = Files.walk(tempDir)) {
            return paths
                .filter(Files::isRegularFile)
                .filter(p -> !p.toString().contains(File.separator + ".git" + File.separator))
                .filter(this::hasAllowedExtension)
                .filter(p -> { try { return Files.size(p) <= maxFileSizeBytes; } catch ... })
                .map(file -> {
                    String content = Files.readString(file, StandardCharsets.UTF_8);
                    String relativePath = tempDir.relativize(file).toString();
                    return new ParsedDocument(relativePath, content, Map.of("filePath", relativePath));
                })
                .toList();
        }
    }
}
```

**Key points:**

- **Shallow clone** (`setDepth(1)`) — only the latest commit, no git history. Saves bandwidth and disk for large repos.
- **File filtering**: `.git/` directory excluded; only files matching `memos.ingestion.git.allowed-extensions` (`.md,.java,.py,.ts,.go,...`); files over 1MB skipped; non-UTF-8 files (binaries) skipped silently.
- **One `ParsedDocument` per file** — each file is a separate ingestion unit, giving the chunker file-level granularity. The `sourceLabel` is the relative file path (e.g. `src/main/java/Foo.java`), which appears in citation `sourceTitle`.
- **Cleanup**: `deleteRecursively` in `finally` — always runs, even if parsing or walking throws. Git repos clone to `/tmp/memos-git-*` which can be many MB, so cleanup is important.
- **No `node_modules`/build artifacts**: naturally excluded by the allowed-extensions whitelist (no `.js` in compiled output and no `.json` at build-output level, though this is imperfect — a real production system would use `.gitignore`-aware walking).

---

## 6. Chunking

`src/main/java/com/memos/infrastructure/ingestion/chunking/SemanticChunkingAdapter.java`

```java
@Component
public class SemanticChunkingAdapter implements ChunkingPort {

    private static final int CHARS_PER_TOKEN = 4;  // rule-of-thumb for English

    private final int chunkSizeTokens;    // default 500, from memos.ingestion.chunk-size-tokens
    private final int overlapTokens;      // default 50,  from memos.ingestion.chunk-overlap-tokens

    @Override
    public List<TextChunk> chunk(String content) {
        // 1. Split on blank lines → paragraphs
        // 2. Greedily fill current chunk until adding next paragraph would exceed chunkSizeTokens
        // 3. On overflow: save current chunk, carry trailing `overlapTokens` of text into next chunk
        // 4. Paragraphs larger than chunkSizeTokens: split on sentence boundaries,
        //    then hard-split by character count as last resort
    }
}
```

**Chunking strategy — paragraph-aware recursive splitting:**

```
Document text
  └─ split on \n\n  →  paragraphs
       ├─ paragraph fits → append to current chunk
       ├─ paragraph would overflow → flush current chunk, start new one with overlap tail
       └─ paragraph itself > chunk size
              └─ split on sentence boundaries (?<=\.!\?)
                   └─ sentence still > chunk size
                          └─ hard split at chunkSizeTokens * 4 chars
```

**Why not fixed-size character/token windows?**
Fixed windows cut mid-sentence and mid-paragraph. A window that starts "...tion of the algorithm. The next step is" provides almost no standalone retrieval value. Paragraph-aware splitting keeps semantic units together — a paragraph typically expresses one idea, making it a better retrieval unit.

**Why not sentence-only chunking?**
Individual sentences are too short. A 15-word sentence embedded alone lacks enough context for semantic similarity to work well. Grouping related sentences (a paragraph) into one chunk gives the embedding model more signal.

**Token estimation:** `ceil(chars / 4)` — no tokenizer dependency. Accurate enough for English prose and sufficient for the chunker's purpose (staying under a target size). Not accurate for code or non-English text — a real production system might use `tiktoken` or the model's own tokenizer.

**Overlap:** The trailing `overlapTokens` (default 50 ≈ 200 chars) of each chunk are prepended to the next chunk. This prevents context loss at chunk boundaries — a sentence split across two chunks can still be found by either.

**`chunk_index`:** A global counter across all `ParsedDocument`s from one ingestion. Used to reconstruct document order and as a stable identifier per chunk within the document.

**`TextChunk` output:**

```java
public record TextChunk(
    String content,
    int tokenCount,
    Map<String, Object> metadata  // currently: {"chunkIndexInSource": N}
) {}
```

---

## 7. Embeddings

`src/main/java/com/memos/infrastructure/ai/embedding/GeminiEmbeddingAdapter.java`

```java
@Slf4j
@Component
public class GeminiEmbeddingAdapter implements EmbeddingPort {

    private static final int CHARS_PER_TOKEN = 4;
    private static final long WINDOW_MILLIS = 60_000L;

    private final Deque<long[]> recentUsage = new ArrayDeque<>();  // [timestamp, estimatedTokens]

    private final OpenAiEmbeddingModel embeddingModel;
    private final int dimensions;        // 768 — Gemini text-embedding-004
    private final int maxBatchSize;      // default 8  (memos.ai.embedding.max-batch-size)
    private final int tokensPerMinute;   // default 10000 (memos.ai.embedding.tokens-per-minute)

    @Override
    @Cacheable(value = "embeddings", key = "#text")
    public float[] embed(String text) {
        return embeddingModel.embed(text);
    }

    @Override
    public List<float[]> embedBatch(List<String> texts) {
        if (texts.size() <= maxBatchSize) {
            awaitCapacity(estimateTokens(texts));
            return embedChunk(texts);
        }

        List<float[]> embeddings = new ArrayList<>(texts.size());
        for (int start = 0; start < texts.size(); start += maxBatchSize) {
            List<String> batch = texts.subList(start, Math.min(start + maxBatchSize, texts.size()));
            awaitCapacity(estimateTokens(batch));
            embeddings.addAll(embedChunk(batch));
        }
        return embeddings;
    }

    private synchronized void awaitCapacity(int tokens) {
        while (true) {
            long now = System.currentTimeMillis();
            recentUsage.removeIf(entry -> now - entry[0] >= WINDOW_MILLIS);

            long used = recentUsage.stream().mapToLong(e -> e[1]).sum();
            if (used == 0 || used + tokens <= tokensPerMinute) {
                recentUsage.addLast(new long[]{now, tokens});
                return;
            }

            long oldestTimestamp = recentUsage.peekFirst()[0];
            long waitMillis = WINDOW_MILLIS - (now - oldestTimestamp) + 1000;
            log.info("Pausing {}ms to stay under the {} tokens/min embedding budget", waitMillis, tokensPerMinute);
            Thread.sleep(Math.max(waitMillis, 1000));
        }
    }
}
```

**What an embedding actually is:** A `float[768]` — a point in 768-dimensional space where semantically similar texts land close together (small cosine distance). The embedding model (Gemini `text-embedding-004`) was trained to produce these vectors such that "cosine similarity ≈ semantic relevance". At query time, the user's question is embedded into the same space, and the closest stored chunk vectors are retrieved.

**Why 768 dimensions?** Gemini's free-tier embedding model (`text-embedding-004`/`gemini-embedding-001`) outputs 768-dimensional vectors. The `vector(768)` column in Postgres and the `@Array(length = 768)` on the entity are fixed to this. Switching to OpenAI (`text-embedding-3-small`, 1536 dims) would require a Flyway migration (`ALTER TABLE document_chunks ALTER COLUMN embedding TYPE vector(1536)`) and re-embedding every existing chunk.

**Rate-limit fix — the before/after:**

*Before:* A single `embeddingModel.embed(allChunks)` call for a 50-page PDF would send ~200 chunks in one request, exceeding Gemini's free-tier tokens-per-minute limit and returning HTTP 429.

*After:* A sliding-window token bucket (`recentUsage` deque):
1. Estimate tokens for the next batch (`chars / 4`).
2. Evict entries older than 60 seconds from the deque.
3. If `used + incoming ≤ tokensPerMinute` — proceed immediately, record usage.
4. If over budget — calculate how long until the oldest entry exits the 60s window, sleep that long, then retry.

*Why batches of 8?* Configurable (`memos.ai.embedding.max-batch-size=8`). At ~500 tokens/chunk × 8 chunks = ~4000 tokens per sub-batch, two batches fit within the 10K TPM budget in the same minute. Default is conservative for the free tier.

*Why 10K tokens/min?* Gemini's free-tier embedding quota is not publicly documented with a precise TPM figure — the 10K default is a conservative approximation based on observed 429 behavior. Both limits are configurable in `application.yml`.

**Gemini via OpenAI-compatible endpoint:** Spring AI 1.0.0 has no dedicated Gemini Developer API starter. Google exposes Gemini through an OpenAI-compatible REST endpoint (`https://generativelanguage.googleapis.com/v1beta/openai`). The adapter uses `spring-ai-starter-model-openai`'s `OpenAiEmbeddingModel`, pointed at Google's URL with a Gemini API key. Spring AI's OpenAI autoconfiguration is excluded in `MemosApplication` so it doesn't conflict.

**`EmbeddingPort` interface:**

```java
public interface EmbeddingPort {
    float[] embed(String text);              // single text, Redis-cached
    List<float[]> embedBatch(List<String> texts);  // batch, rate-limited
    int dimensions();                        // 768 for Gemini
}
```

---

## 8. Vector storage & retrieval

### Entity

`src/main/java/com/memos/infrastructure/persistence/entity/DocumentChunkEntity.java`

```java
@Entity
@Table(name = "document_chunks")
public class DocumentChunkEntity {

    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "document_id", nullable = false)
    private UUID documentId;

    @Column(name = "chunk_index", nullable = false)
    private int chunkIndex;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    @JdbcTypeCode(SqlTypes.VECTOR)
    @Array(length = 768)
    @Column(nullable = false)
    private float[] embedding;   // stored as pgvector vector(768)

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> metadata;

    // ...
}
```

`@JdbcTypeCode(SqlTypes.VECTOR)` + `@Array(length = 768)` — Hibernate 6.6's `hibernate-vector` extension maps `float[]` ↔ pgvector's `vector` type. Without this, Hibernate would try to store a `float[]` as a Postgres array (`float4[]`), which is a different type that doesn't support `<=>` (cosine distance) operators.

### Cosine similarity retrieval

`src/main/java/com/memos/infrastructure/persistence/repository/DocumentChunkJpaRepository.java`

```java
@Query(value = """
        SELECT c.id AS id,
               c.document_id AS documentId,
               c.chunk_index AS chunkIndex,
               c.content AS content,
               d.title AS sourceTitle,
               (1 - (c.embedding <=> CAST(:queryVector AS vector))) AS score
        FROM document_chunks c
        JOIN documents d ON d.id = c.document_id
        WHERE d.owner_id = :ownerId
          AND d.status = 'COMPLETED'
        ORDER BY c.embedding <=> CAST(:queryVector AS vector)
        LIMIT :topK
        """, nativeQuery = true)
List<SimilarChunkProjection> findSimilarByOwner(
        @Param("ownerId") UUID ownerId,
        @Param("queryVector") String queryVector,
        @Param("topK") int topK);
```

**`<=>` operator:** pgvector's cosine distance operator. Returns a value in [0, 2] where 0 = identical direction, 2 = opposite direction. Score is converted to cosine similarity: `1 - cosine_distance`, so 1.0 = perfect match.

**Why cosine similarity over Euclidean distance?** Embedding vectors represent direction in semantic space, not magnitude. Two texts with the same meaning but different lengths would produce vectors pointing in the same direction but with different magnitudes. Cosine similarity measures the angle between vectors, making it magnitude-independent. It's the standard for text embeddings.

**Why approximate (HNSW) over exact?** An exact scan compares the query vector against every row — O(n) operations per query. At 100K chunks this is tolerable; at 10M it becomes seconds. HNSW (Hierarchical Navigable Small World) builds a graph where each node knows its approximate nearest neighbors. Queries traverse the graph in O(log n). The tradeoff: HNSW may miss the true top-K in edge cases (controllable via `ef_search`). For RAG, approximate recall is fine — returning the 4th-most-relevant chunk instead of the 5th doesn't matter.

**Owner scoping:** `WHERE d.owner_id = :ownerId` — users can only retrieve chunks from their own documents. Combined with `AND d.status = 'COMPLETED'`, chunks from in-progress or failed ingestions are never returned.

**`RetrievalAdapter`:**

```java
@Component
@RequiredArgsConstructor
public class RetrievalAdapter implements RetrievalPort {

    private final DocumentChunkJpaRepository jpaRepository;

    @Override
    public List<RetrievedChunk> findSimilar(UUID ownerId, float[] queryEmbedding, int topK) {
        String queryVector = toVectorLiteral(queryEmbedding);  // "[0.1,0.2,...]"
        return jpaRepository.findSimilarByOwner(ownerId, queryVector, topK)
                .stream().map(this::toRetrievedChunk).toList();
    }

    private String toVectorLiteral(float[] embedding) {
        StringBuilder builder = new StringBuilder(embedding.length * 10);
        builder.append('[');
        for (int i = 0; i < embedding.length; i++) {
            if (i > 0) builder.append(',');
            builder.append(embedding[i]);
        }
        builder.append(']');
        return builder.toString();
    }
}
```

The query vector is passed as a string literal because JPA's `@Query` with `nativeQuery = true` doesn't know how to bind a `float[]` as a pgvector type. `CAST(:queryVector AS vector)` lets Postgres parse the `[...]` literal into the correct type at query time.

---

## 9. Redis embedding cache

`src/main/java/com/memos/infrastructure/cache/RedisConfig.java`

```java
@Configuration
public class RedisConfig {

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        RedisCacheConfiguration configuration = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofHours(1))
                .disableCachingNullValues()
                .serializeValuesWith(fromSerializer(new GenericJackson2JsonRedisSerializer()));

        // float[] needs a type-bound serializer — GenericJackson2Json's default typing
        // writes primitive arrays without a wrapper but reads Object-typed values expecting one.
        RedisCacheConfiguration embeddingsConfiguration = configuration.serializeValuesWith(
                fromSerializer(new Jackson2JsonRedisSerializer<>(float[].class)));

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(configuration)
                .withInitialCacheConfigurations(Map.of("embeddings", embeddingsConfiguration))
                .build();
    }
}
```

`@Cacheable(value = "embeddings", key = "#text")` on `GeminiEmbeddingAdapter.embed()` — single-text embedding calls are cached by the exact input text with a 1-hour TTL. Benefits:

- **RAG queries**: the same user question asked repeatedly (or similar questions that have the same embedding) don't re-spend free-tier quota.
- **Ingestion retries**: if a document fails at chunk 40 and is re-ingested, the first 39 chunks' embeddings are already in cache.

The separate `"embeddings"` cache config uses `Jackson2JsonRedisSerializer<float[]>` instead of `GenericJackson2JsonRedisSerializer` — the generic serializer's polymorphic type wrapper doesn't round-trip correctly for primitive arrays.

---

## 10. Flyway migrations

### V3 — documents

```sql
CREATE TABLE documents (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_id        UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    title           VARCHAR(500) NOT NULL,
    source_type     VARCHAR(30) NOT NULL,   -- PDF | MARKDOWN | GIT_REPOSITORY
    source_uri      TEXT,                   -- temp file path (PDF/MD) or repo URL (Git)
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING',  -- PENDING | PROCESSING | COMPLETED | FAILED
    error_message   TEXT,
    metadata        JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
```

### V4 — pgvector + document_chunks

```sql
CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE document_chunks (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    document_id     UUID NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
    chunk_index     INT NOT NULL,
    content         TEXT NOT NULL,
    embedding       vector(768) NOT NULL,   -- fixed to Gemini's 768-dim output
    token_count     INT NOT NULL DEFAULT 0,
    metadata        JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_document_chunks_document_id ON document_chunks (document_id);
CREATE INDEX idx_document_chunks_embedding_hnsw
    ON document_chunks USING hnsw (embedding vector_cosine_ops);
```

`vector_cosine_ops` tells the HNSW index to optimize for cosine distance (`<=>`). If you changed the distance metric to Euclidean or dot product, you'd need a different index (`vector_l2_ops` or `vector_ip_ops`).

### V8 — additional indexes

```sql
-- For paginated document list, ordered by most recent first
CREATE INDEX idx_documents_owner_id_created_at ON documents (owner_id, created_at DESC);
-- For paginated conversation list, ordered by most recently active
CREATE INDEX idx_conversations_user_id_updated_at ON conversations (user_id, updated_at DESC);
```

Composite indexes covering both the filter column and the sort column avoid a full-table sort.

---

## 11. End-to-end flow summary

```
POST /api/v1/documents/upload  (multipart: file + title)
  │
  ├─ DocumentController.upload()
  │   ├─ resolveSourceType(file)  →  PDF | MARKDOWN
  │   ├─ storeTempFile(file)      →  /tmp/memos-upload-<uuid>.pdf
  │   └─ documentIngestionService.submit(...)
  │         └─ INSERT documents (status=PENDING)  →  return 202 DocumentResponse
  │
  └─ documentIngestionService.ingest(documentId)  [dispatched @Async, returns immediately]
        │
        ├─ UPDATE documents SET status='PROCESSING'
        │
        ├─ parserRegistry.resolve(PDF)
        │   └─ PdfDocumentParser.parse(document)
        │         ├─ Loader.loadPDF(tempFile)
        │         ├─ PDFTextStripper.getText()
        │         ├─ strip NUL bytes
        │         ├─ repairCaesarShift()
        │         └─ → [ParsedDocument(title, fullText, {pageCount: N})]
        │
        ├─ [for each ParsedDocument]
        │   ├─ SemanticChunkingAdapter.chunk(content)
        │   │     ├─ split on \n\n → paragraphs
        │   │     ├─ greedily fill chunks ≤500 tokens with 50-token overlap
        │   │     └─ → [TextChunk(text, tokenCount, metadata), ...]
        │   │
        │   └─ GeminiEmbeddingAdapter.embedBatch(chunkTexts)
        │         ├─ split into sub-batches of ≤8 texts
        │         ├─ for each sub-batch: awaitCapacity() [token-bucket rate limiter]
        │         ├─ OpenAiEmbeddingModel.embed(batch) → Gemini endpoint
        │         └─ → [float[768], float[768], ...]
        │
        ├─ documentChunkRepositoryPort.saveAll(chunksToSave)
        │     └─ INSERT document_chunks (content, embedding, chunk_index, ...)  [@Transactional]
        │
        ├─ UPDATE documents SET status='COMPLETED'
        │
        └─ [finally] Files.deleteIfExists(/tmp/memos-upload-<uuid>.pdf)

──────────────────────────────────────────────────────────────────────────

POST /api/v1/conversations/{id}/messages  {"content": "What does X mean?"}
  │
  ├─ RagQueryService.askQuestion(userId, conversationId, question)
  │   ├─ INSERT messages (role=USER, content=question)
  │   │
  │   ├─ GeminiEmbeddingAdapter.embed(question)   [Redis cache checked first]
  │   │     └─ → float[768]  (query vector)
  │   │
  │   ├─ RetrievalAdapter.findSimilar(userId, queryVector, topK=5)
  │   │     └─ SELECT ... FROM document_chunks JOIN documents
  │   │              WHERE owner_id = ? AND status = 'COMPLETED'
  │   │              ORDER BY embedding <=> ? LIMIT 5   [HNSW index]
  │   │        → [RetrievedChunk(chunkId, documentId, content, score), ...]
  │   │
  │   ├─ buildPrompt(conversationId, retrievedChunks)
  │   │     ├─ system message: "You are MemOS... context: [1] source... [2] source..."
  │   │     └─ recent N messages from conversation history
  │   │
  │   ├─ chatPortResolver.resolve(conversation.provider)  →  GeminiChatAdapter | ...
  │   ├─ chatPort.complete(promptMessages)                →  LLM API call
  │   │
  │   └─ INSERT messages (role=ASSISTANT, content=llmReply)
  │         + INSERT citations (documentId, chunkId, snippet, score) per chunk
  │
  └─ 201 MessageResponse { id, role, content, citations: [...] }
```

---

## 12. Interview Q&A

**"Walk me through what happens between a user clicking upload and the document being searchable."**

See §11 above. Key beats: controller writes PENDING + returns 202 immediately → `@Async ingest` dispatches on a virtual thread → parse → chunk (paragraph-aware, 500 tokens, 50-token overlap) → embed in rate-limited sub-batches (≤8 chunks, ≤10K tokens/min) → `saveAll` chunks with vectors → status = COMPLETED. The document is queryable as soon as COMPLETED is written and the HNSW index has indexed the new rows (pgvector updates the HNSW index synchronously on insert).

---

**"Why did you choose to chunk this way? What's wrong with naive fixed-size chunking?"**

Fixed-size character windows cut mid-sentence — a chunk starting "...tion of the approach. The next" is nearly meaningless as a standalone retrieval unit. Paragraph-aware splitting keeps semantic units together. Each paragraph typically expresses one idea, giving the embedding model a coherent piece of text to represent. Code is chunked the same way currently (splitting on blank lines); a more sophisticated approach would use AST-level splitting per function/class.

---

**"How do you decide chunk size? What's the tradeoff?"**

500 tokens (≈2000 chars) is a middle ground. Smaller chunks (100 tokens) give higher precision — retrieved chunks closely match the question — but individual chunks may lack context and the answer may be spread across chunks you didn't retrieve. Larger chunks (2000 tokens) capture more context but the embedding represents a broader average of meaning, reducing precision, and you hit the model's context window faster. 500 tokens is a commonly cited sweet spot for general-purpose RAG.

---

**"What happens if embedding generation fails for one chunk out of 50?"**

The entire document fails. `embedBatch` throws an `AiProviderException` on HTTP 429 or network failure, which propagates out of `buildChunks`, is caught by `ingest`'s `catch (Exception e)`, and the document is marked `FAILED` with the error message. Because `saveAll` hasn't been called yet (embedding happens before persistence), no partial chunks are committed.

The gap: if the failure happens mid-batch on attempt 2 of 6, chunks from attempt 1 haven't been persisted (good — no partial state), but the retry logic doesn't resume — it restarts from scratch. For large repos with many files, this means re-embedding all successfully embedded files.

---

**"Why HNSW over a flat/brute-force vector index?"**

Brute-force scans every row: O(n) cosine computations per query. At 10K chunks it's fast; at 10M chunks it's seconds. HNSW builds a multi-layer graph of approximate neighbors, enabling sub-linear traversal. The tradeoff: HNSW has ~5–10% recall error — it may return the 4th or 5th most similar chunk instead of the 2nd. For RAG this is acceptable. You can tune recall vs. speed with the `ef_search` parameter (not exposed in the current config).

---

**"You hit a 429 rate limit — walk me through your fix. Why batches of 8? Why 10K tokens/min?"**

*Before:* `embeddingModel.embed(allChunks)` sent all chunks in one request. A 50-page PDF produces ~200 chunks × 500 tokens each = ~100K tokens in one minute. Gemini's free tier returned 429.

*Fix:* A sliding-window token bucket in `awaitCapacity()`. A `Deque<long[]>` tracks `[timestamp, estimatedTokens]` for recent requests. Before each sub-batch: evict entries older than 60s, sum remaining usage, block until there's capacity. This paces the total tokens sent in any rolling 60-second window to stay under `tokensPerMinute` (default 10K).

*Why 8?* At ~500 tokens/chunk × 8 chunks = ~4K tokens per sub-batch, 2 sub-batches fit comfortably in 10K TPM without sleeping. It's configurable — not a magic number.

*Why 10K?* A conservative approximation of Gemini's free-tier embedding quota based on observed 429 errors. The actual limit is not publicly documented. Both values are in `application.yml` and can be tuned per account/tier.

---

**"If you wanted to switch from Gemini to OpenAI embeddings, what would break and how would you migrate?"**

1. **Schema**: `document_chunks.embedding` is `vector(768)`. OpenAI's `text-embedding-3-small` outputs 1536 dims. Flyway migration: `ALTER TABLE document_chunks ALTER COLUMN embedding TYPE vector(1536)` — this requires `pgvector` 0.5+ and rebuilds the HNSW index.
2. **All existing chunks must be re-embedded**: their 768-dim vectors are meaningless in a 1536-dim space. You'd need a migration job that reads all chunks and calls the new embedding model.
3. **Config**: set `memos.ai.embedding.provider=OPENAI`, add an `EmbeddingPort` implementation for OpenAI (or update `GeminiEmbeddingAdapter` to read from a configurable provider).
4. **Redis cache**: all cached 768-dim vectors would deserialize incorrectly — flush the `embeddings` cache before starting.

---

**"How would you detect and avoid re-embedding a document that hasn't changed?"**

Not currently implemented. A production approach: store a content hash (`SHA-256` of the raw file or repo commit SHA) in `documents.metadata` at upload time. On a re-upload of the same file, check if the hash matches an existing COMPLETED document for the same owner — if so, skip ingestion and return the existing document. For Git repos, the commit SHA could be stored after cloning and checked against the latest remote HEAD before re-cloning.

---

**"Is your async processing durable? If the server crashes while a document is PROCESSING, what happens on restart?"**

**Honest answer: no.** Documents stuck in `PROCESSING` at crash time remain `PROCESSING` forever after restart. There is no recovery mechanism.

The data is consistent — no partial chunks are committed because a crash mid-`saveAll` would roll back the transaction. But the document status never transitions to `COMPLETED` or `FAILED`, making the document appear perpetually in-progress to the user.

**How to fix it:** A `@EventListener(ApplicationStartedEvent.class)` or `CommandLineRunner` at startup that resets all `PROCESSING` documents to `PENDING` and re-queues them for ingestion. A more robust approach uses a durable job queue (e.g. Postgres-backed queue with `SELECT FOR UPDATE SKIP LOCKED`, or an external queue like SQS) so ingestion jobs survive process restarts and can be retried with backoff.
