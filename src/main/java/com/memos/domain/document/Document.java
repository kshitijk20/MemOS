package com.memos.domain.document;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder(toBuilder = true)
@AllArgsConstructor
public class Document {

    private final UUID id;
    private final UUID ownerId;
    private final String title;
    private final DocumentSourceType sourceType;
    private final String sourceUri;
    private final DocumentStatus status;
    private final String errorMessage;
    private final Map<String, Object> metadata;
    private final Instant createdAt;
    private final Instant updatedAt;

    public Document withStatus(DocumentStatus newStatus) {
        return this.toBuilder().status(newStatus).updatedAt(Instant.now()).build();
    }

    public Document withFailure(String message) {
        return this.toBuilder()
                .status(DocumentStatus.FAILED)
                .errorMessage(message)
                .updatedAt(Instant.now())
                .build();
    }
}
