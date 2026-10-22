package com.memos.web.collection;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.memos.domain.collection.Collection;

public record CollectionResponse(
        UUID id,
        String name,
        List<UUID> documentIds,
        Instant createdAt,
        Instant updatedAt) {

    public static CollectionResponse from(Collection collection) {
        return new CollectionResponse(
                collection.getId(),
                collection.getName(),
                collection.getDocumentIds() == null ? List.of() : List.copyOf(collection.getDocumentIds()),
                collection.getCreatedAt(),
                collection.getUpdatedAt());
    }
}
