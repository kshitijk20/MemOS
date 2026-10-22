package com.memos.web.document;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import com.memos.domain.document.Document;
import com.memos.domain.document.DocumentChunk;
import com.memos.domain.document.DocumentSourceType;

public record DocumentChunkSourceResponse(
        UUID id,
        UUID documentId,
        int chunkIndex,
        String content,
        int tokenCount,
        Map<String, Object> metadata,
        Instant createdAt,
        String documentTitle,
        String sourceUri,
        DocumentSourceType sourceType,
        /** Text of the chunks just before and after, for showing the cited passage in context. */
        String previousContent,
        String nextContent) {

    public static DocumentChunkSourceResponse from(DocumentChunk chunk, Document document) {
        return from(chunk, document, null, null);
    }

    public static DocumentChunkSourceResponse from(
            DocumentChunk chunk, Document document, DocumentChunk previous, DocumentChunk next) {
        return new DocumentChunkSourceResponse(
                chunk.getId(),
                chunk.getDocumentId(),
                chunk.getChunkIndex(),
                chunk.getContent(),
                chunk.getTokenCount(),
                chunk.getMetadata(),
                chunk.getCreatedAt(),
                document.getTitle(),
                document.getSourceUri(),
                document.getSourceType(),
                previous == null ? null : previous.getContent(),
                next == null ? null : next.getContent());
    }
}
