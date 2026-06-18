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
import com.memos.infrastructure.ingestion.parser.DocumentParserRegistry;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Orchestrates document ingestion: parsing the source into text, chunking it, embedding each
 * chunk, and persisting the results. {@link #submit} returns immediately with a
 * {@code PENDING} document; {@link #ingest} performs the actual work asynchronously.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentIngestionService {

    private final DocumentRepositoryPort documentRepositoryPort;
    private final DocumentChunkRepositoryPort documentChunkRepositoryPort;
    private final DocumentParserRegistry parserRegistry;
    private final ChunkingPort chunkingPort;
    private final EmbeddingPort embeddingPort;

    /**
     * Creates a {@code PENDING} document row. Callers should follow up with {@link #ingest}
     * (a different bean than the caller, so the {@code @Async} proxy applies) to start
     * processing.
     */
    public Document submit(UUID ownerId, String title, DocumentSourceType sourceType, String sourceUri, Map<String, Object> metadata) {
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
            deleteTempSourceFile(document);
        }
    }

    /**
     * Deletes the temp file written by the upload endpoint for file-based source types (PDF,
     * MARKDOWN). Git clones clean themselves up inside {@code GitRepositoryParser}. The check
     * on the path prefix avoids accidentally deleting a user-supplied absolute path if the
     * source URI was somehow set to a non-temp value.
     */
    private void deleteTempSourceFile(Document document) {
        if (document == null) {
            return;
        }
        DocumentSourceType type = document.getSourceType();
        if (type != DocumentSourceType.PDF && type != DocumentSourceType.MARKDOWN) {
            return;
        }
        String sourceUri = document.getSourceUri();
        if (sourceUri == null || !sourceUri.contains("memos-upload-")) {
            return;
        }
        try {
            Files.deleteIfExists(Path.of(sourceUri));
        } catch (IOException e) {
            log.warn("Failed to delete temp source file {}: {}", sourceUri, e.getMessage());
        }
    }

    private List<DocumentChunk> buildChunks(Document document, List<ParsedDocument> parsedDocuments) {
        List<DocumentChunk> chunksToSave = new ArrayList<>();
        int globalIndex = 0;

        for (ParsedDocument parsed : parsedDocuments) {
            List<TextChunk> textChunks = chunkingPort.chunk(parsed.content());
            if (textChunks.isEmpty()) {
                continue;
            }

            List<float[]> embeddings = embeddingPort.embedBatch(textChunks.stream().map(TextChunk::content).toList());

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
