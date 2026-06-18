package com.memos.web.document;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import com.memos.domain.document.DocumentChunk;

public record DocumentChunkResponse(
        UUID id,
        int chunkIndex,
        String content,
        int tokenCount,
        Map<String, Object> metadata,
        Instant createdAt) {

    public static DocumentChunkResponse from(DocumentChunk chunk) {
        return new DocumentChunkResponse(
                chunk.getId(),
                chunk.getChunkIndex(),
                chunk.getContent(),
                chunk.getTokenCount(),
                chunk.getMetadata(),
                chunk.getCreatedAt());
    }
}
