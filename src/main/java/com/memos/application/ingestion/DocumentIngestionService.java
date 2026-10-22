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

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

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

    public Document submit(
            UUID ownerId,
            String title,
            DocumentSourceType sourceType,
            String sourceUri,
            String sourceFingerprint,
            Map<String, Object> metadata) {
        rejectDuplicate(ownerId, sourceFingerprint, null);

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
        return documentRepositoryPort.save(document.resetForRetry());
    }

    public Document reingest(Document document) {
        if (document.getStatus() == DocumentStatus.PENDING || document.getStatus() == DocumentStatus.PROCESSING) {
            throw new IngestionException("This document is already being ingested.");
        }
        return documentRepositoryPort.save(document.prepareForReingest());
    }

    public Document replaceFileSource(Document document, String sourceUri, String fingerprint, Map<String, Object> metadata) {
        rejectDuplicate(document.getOwnerId(), fingerprint, document.getId());
        return documentRepositoryPort.save(document.withSource(sourceUri, fingerprint, metadata));
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
            documentChunkRepositoryPort.deleteByDocumentId(documentId);
            documentChunkRepositoryPort.saveAll(chunksToSave);

            document = document.withStatus(DocumentStatus.COMPLETED);
            documentRepositoryPort.save(document);
            log.info("Ingestion completed for document {} ({} chunks, v{})", documentId, chunksToSave.size(), document.getVersion());
        } catch (Exception e) {
            log.error("Ingestion failed for document {}", documentId, e);
            documentRepositoryPort.save(document.withFailure(ProviderErrorMessages.sanitizeIngestionFailure(e.getMessage())));
        } finally {
            progressStore.remove(documentId);
            deleteTempSourceFile(document);
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
            case PDF, MARKDOWN -> null;
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
        DocumentSourceType type = document.getSourceType();
        if (type != DocumentSourceType.PDF && type != DocumentSourceType.MARKDOWN) {
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

    private void deleteTempSourceFile(Document document) {
        if (document == null) return;
        DocumentSourceType type = document.getSourceType();
        if (type != DocumentSourceType.PDF && type != DocumentSourceType.MARKDOWN) return;
        String sourceUri = document.getSourceUri();
        if (sourceUri == null || !sourceUri.contains("memos-upload-")) return;
        try {
            Files.deleteIfExists(Path.of(sourceUri));
        } catch (IOException e) {
            log.warn("Failed to delete temp source file {}: {}", sourceUri, e.getMessage());
        }
    }
}
