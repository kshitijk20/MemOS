package com.memos.domain.collection;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/** A user-named group of documents (e.g. "HR policies") that a conversation can be limited to. */
@Getter
@Builder(toBuilder = true)
@AllArgsConstructor
public class Collection {

    public static final int MAX_NAME_LENGTH = 100;

    private final UUID id;
    private final UUID ownerId;
    private final String name;
    private final Set<UUID> documentIds;
    private final Instant createdAt;
    private final Instant updatedAt;
}
