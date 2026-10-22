package com.memos.web.document;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import com.memos.domain.document.Document;
import com.memos.domain.document.DocumentSourceType;
import com.memos.domain.document.DocumentStatus;

public record DocumentResponse(
        UUID id,
        String title,
        DocumentSourceType sourceType,
        String sourceUri,
        DocumentStatus status,
        String errorMessage,
        int version,
        Map<String, Object> metadata,
        Instant createdAt,
        Instant updatedAt) {

    public static DocumentResponse from(Document document) {
        return new DocumentResponse(
                document.getId(),
                document.getTitle(),
                document.getSourceType(),
                document.getSourceUri(),
                document.getStatus(),
                document.getErrorMessage(),
                document.getVersion(),
                document.getMetadata(),
                document.getCreatedAt(),
                document.getUpdatedAt());
    }
}
