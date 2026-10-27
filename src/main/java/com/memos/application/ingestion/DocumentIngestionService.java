package com.memos.application.ingestion;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.memos.application.limits.UsageLimitService;
import com.memos.domain.ai.EmbeddingPort;
import com.memos.domain.document.Document;
import com.memos.domain.document.DocumentChunk;
import com.memos.domain.document.DocumentChunkRepositoryPort;
import com.memos.domain.document.DocumentRepositoryPort;
import com.memos.domain.document.DocumentSourceType;
import com.memos.domain.document.DocumentStatus;
import com.memos.domain.exception.DuplicateResourceException;
import com.memos.domain.exception.IngestionException;
import com.memos.domain.exception.ProviderErrorMessages;
import com.memos.infrastructure.ingestion.parser.DocumentParserRegistry;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentIngestionService {

    private static final int EMBED_BATCH_SIZE = 10;
    static final String INTERRUPTED_MESSAGE =
            "Ingestion was interrupted because the application restarted. Retry to continue.";
    static final String MISSING_UPLOAD_MESSAGE =
            "Original upload is no longer on disk. Re-ingest and upload the file again.";

    private final DocumentRepositoryPort documentRepositoryPort;
    private final DocumentChunkRepositoryPort documentChunkRepositoryPort;
    private final DocumentParserRegistry parserRegistry;
    private final ChunkingPort chunkingPort;
    private final EmbeddingPort embeddingPort;
    private final IngestionProgressStore progressStore;
    private final UsageLimitService usageLimitService;

    /** Documents one user may have; 0 disables the limit. */
    @Value("${memos.limits.max-documents-per-user:100}")
    private int maxDocumentsPerUser = 100;

    /** Stored chunks (search sections) one user may have across all documents; 0 disables the limit. */
    @Value("${memos.limits.max-chunks-per-user:20000}")
    private int maxChunksPerUser = 20000;

    public Document submit(
            UUID ownerId,
            String title,
            DocumentSourceType sourceType,
            String sourceUri,
            String sourceFingerprint,
            Map<String, Object> metadata) {
        rejectDuplicate(ownerId, sourceFingerprint, null);
        if (maxDocumentsPerUser > 0 && documentRepositoryPort.countByOwnerId(ownerId) >= maxDocumentsPerUser) {
            throw new IngestionException("You've reached the limit of " + maxDocumentsPerUser
                    + (maxDocumentsPerUser == 1 ? " document" : " documents") + ". Delete some to add more.");
        }
        usageLimitService.checkIngestion(ownerId);

        Instant now = Instant.now();
        Document document = Document.builder()
                .ownerId(ownerId)
                .title(title)
                .sourceType(sourceType)
                .sourceUri(sourceUri)
                .sourceFingerprint(sourceFingerprint)
                .status(DocumentStatus.PENDING)
                .version(1)
                .metadata(metadata)
                .createdAt(now)
                .updatedAt(now)
                .build();
        return documentRepositoryPort.save(document);
    }

    public Document retry(Document document) {
        if (document.getStatus() != DocumentStatus.FAILED) {
            throw new IngestionException("Only failed documents can be retried.");
        }
        usageLimitService.checkIngestion(document.getOwnerId());
        return documentRepositoryPort.save(document.resetForRetry());
    }

    public Document reingest(Document document) {
        rejectInFlight(document);
        usageLimitService.checkIngestion(document.getOwnerId());
        return documentRepositoryPort.save(document.prepareForReingest());
    }

    private static void rejectInFlight(Document document) {
        if (document.getStatus() == DocumentStatus.PENDING || document.getStatus() == DocumentStatus.PROCESSING) {
            throw new IngestionException("This document is already being ingested.");
        }
    }

    /** Swaps in a newly uploaded file and queues the document for re-ingestion (returns it PENDING). */
    public Document replaceFileSource(Document document, String sourceUri, String fingerprint, Map<String, Object> metadata) {
        rejectInFlight(document);
        rejectDuplicate(document.getOwnerId(), fingerprint, document.getId());
        usageLimitService.checkIngestion(document.getOwnerId());
        Document updated = documentRepositoryPort.save(
                document.withSource(sourceUri, fingerprint, metadata).prepareForReingest());
        // A failed ingestion keeps its upload for Retry; once replaced, that copy is unreachable.
        if (!sourceUri.equals(document.getSourceUri())) {
            deleteTempSourceFile(document);
        }
        return updated;
    }

    /** Deletes the document (its chunks cascade) and any upload still kept on disk for it. */
    public void delete(Document document) {
        documentRepositoryPort.deleteById(document.getId());
        deleteTempSourceFile(document);
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

            ensureSourceAvailable(document);

            progressStore.update(documentId, "PARSING", 0, 0);
            DocumentParserPort parser = parserRegistry.resolve(document.getSourceType());
            List<ParsedDocument> parsedDocuments = parser.parse(document);

            progressStore.update(documentId, "CHUNKING", 0, 0);
            List<DocumentChunk> chunksToSave = buildChunksWithProgress(document, parsedDocuments);

            progressStore.update(documentId, "SAVING", chunksToSave.size(), chunksToSave.size());
            documentChunkRepositoryPort.replaceForDocument(documentId, chunksToSave);

            document = document.withStatus(DocumentStatus.COMPLETED);
            documentRepositoryPort.save(document);
            log.info("Ingestion completed for document {} ({} chunks, v{})", documentId, chunksToSave.size(), document.getVersion());
            deleteTempSourceFile(document);
        } catch (Exception e) {
            // The upload stays on disk so Retry can resume; chunks embedded so far are cached
            // by the embedding adapter and won't be re-embedded.
            log.error("Ingestion failed for document {}", documentId, e);
            documentRepositoryPort.save(document.withFailure(ProviderErrorMessages.sanitizeIngestionFailure(e.getMessage())));
        } finally {
            progressStore.remove(documentId);
        }
    }

    public void recoverInterruptedIngestion() {
        backfillFingerprints();

        List<Document> stuck = documentRepositoryPort.findAllByStatus(DocumentStatus.PROCESSING);
        for (Document document : stuck) {
            log.warn("Marking stuck PROCESSING document {} as FAILED after restart", document.getId());
            documentRepositoryPort.save(document.withFailure(INTERRUPTED_MESSAGE));
        }
    }

    public List<Document> findPendingDocuments() {
        return documentRepositoryPort.findAllByStatus(DocumentStatus.PENDING);
    }

    private void backfillFingerprints() {
        for (Document document : documentRepositoryPort.findAllBySourceFingerprintIsNull()) {
            String fingerprint = fingerprintFromExisting(document);
            if (fingerprint == null) {
                continue;
            }
            try {
                rejectDuplicate(document.getOwnerId(), fingerprint, document.getId());
                documentRepositoryPort.save(document.toBuilder().sourceFingerprint(fingerprint).build());
            } catch (DuplicateResourceException e) {
                log.warn("Skipping fingerprint backfill for document {}: {}", document.getId(), e.getMessage());
            }
        }
    }

    private String fingerprintFromExisting(Document document) {
        return switch (document.getSourceType()) {
            case WEB_PAGE -> document.getSourceUri() == null ? null : SourceFingerprint.forWebPage(document.getSourceUri());
            case GIT_REPOSITORY -> {
                String branch = metadataString(document, "branch");
                yield document.getSourceUri() == null ? null : SourceFingerprint.forGit(document.getSourceUri(), branch);
            }
            case PDF, MARKDOWN, IMAGE -> null;
        };
    }

    private static String metadataString(Document document, String key) {
        Object value = document.getMetadata() == null ? null : document.getMetadata().get(key);
        return value == null ? null : value.toString();
    }

    private void rejectDuplicate(UUID ownerId, String fingerprint, UUID ignoreDocumentId) {
        if (fingerprint == null || fingerprint.isBlank()) {
            return;
        }
        documentRepositoryPort.findByOwnerIdAndSourceFingerprint(ownerId, fingerprint)
                .filter(existing -> ignoreDocumentId == null || !existing.getId().equals(ignoreDocumentId))
                .ifPresent(existing -> {
                    throw new DuplicateResourceException(
                            "This source is already ingested as \"" + existing.getTitle()
                                    + "\" (" + existing.getStatus() + "). Use Re-ingest on that document, or delete it first.");
                });
    }

    private void ensureSourceAvailable(Document document) {
        if (!document.getSourceType().isUploadedFile()) {
            return;
        }
        String sourceUri = document.getSourceUri();
        if (sourceUri == null || !Files.isRegularFile(Path.of(sourceUri))) {
            throw new IngestionException(MISSING_UPLOAD_MESSAGE);
        }
    }

    private List<DocumentChunk> buildChunksWithProgress(Document document, List<ParsedDocument> parsedDocuments) {
        record ChunkEntry(TextChunk textChunk, Map<String, Object> metadata) {}

        List<ChunkEntry> allChunks = new ArrayList<>();
        for (ParsedDocument parsed : parsedDocuments) {
            List<TextChunk> textChunks = chunkingPort.chunk(parsed.content());
            for (TextChunk tc : textChunks) {
                Map<String, Object> meta = new HashMap<>();
                meta.put("sourceLabel", parsed.sourceLabel());
                meta.putAll(parsed.metadata());
                meta.putAll(tc.metadata());
                allChunks.add(new ChunkEntry(tc, meta));
            }
        }

        int total = allChunks.size();
        enforceChunkLimit(document, total);
        progressStore.update(document.getId(), "EMBEDDING", 0, total);

        List<DocumentChunk> result = new ArrayList<>(total);
        int globalIndex = 0;

        for (int batchStart = 0; batchStart < total; batchStart += EMBED_BATCH_SIZE) {
            int batchEnd = Math.min(batchStart + EMBED_BATCH_SIZE, total);
            List<ChunkEntry> batch = allChunks.subList(batchStart, batchEnd);

            List<float[]> embeddings = embeddingPort.embedBatch(
                    batch.stream().map(e -> e.textChunk().content()).toList());

            for (int i = 0; i < batch.size(); i++) {
                ChunkEntry entry = batch.get(i);
                result.add(DocumentChunk.builder()
                        .documentId(document.getId())
                        .chunkIndex(globalIndex++)
                        .content(entry.textChunk().content())
                        .embedding(embeddings.get(i))
                        .tokenCount(entry.textChunk().tokenCount())
                        .metadata(entry.metadata())
                        .createdAt(Instant.now())
                        .build());
            }

            progressStore.update(document.getId(), "EMBEDDING", batchEnd, total);
        }

        return result;
    }

    /** Runs before embedding, so an over-limit document fails without spending embedding quota. */
    private void enforceChunkLimit(Document document, int newChunks) {
        if (maxChunksPerUser <= 0) {
            return;
        }
        // Re-ingesting replaces this document's own chunks, so they don't count against it.
        long used = documentChunkRepositoryPort.countByOwnerId(document.getOwnerId())
                - documentChunkRepositoryPort.countByDocumentId(document.getId());
        if (used + newChunks > maxChunksPerUser) {
            throw new IngestionException("This document has " + newChunks + " sections, which would exceed your storage "
                    + "limit (" + used + " of " + maxChunksPerUser + " used). Delete documents to free space.");
        }
    }

    private void deleteTempSourceFile(Document document) {
        if (document == null) return;
        if (!document.getSourceType().isUploadedFile()) return;
        String sourceUri = document.getSourceUri();
        if (sourceUri == null || !sourceUri.contains("memos-upload-")) return;
        try {
            Files.deleteIfExists(Path.of(sourceUri));
        } catch (IOException e) {
            log.warn("Failed to delete temp source file {}: {}", sourceUri, e.getMessage());
        }
    }
}
