package com.memos.domain.conversation;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.memos.domain.ai.AiProvider;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder(toBuilder = true)
@AllArgsConstructor
public class Conversation {

    private final UUID id;
    private final UUID userId;
    private final String title;
    private final AiProvider provider;
    private final String model;
    private final List<UUID> documentScope;
    private final Instant createdAt;
    private final Instant updatedAt;

    public Conversation withTouchedTimestamp() {
        return this.toBuilder().updatedAt(Instant.now()).build();
    }
}
