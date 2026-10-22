package com.memos.web.collection;

import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.Size;

/** Fields left null are unchanged; {@code documentIds} replaces the whole membership. */
public record UpdateCollectionRequest(
        @Size(max = 100)
        String name,
        List<UUID> documentIds) {
}
