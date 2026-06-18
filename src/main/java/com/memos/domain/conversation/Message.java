package com.memos.domain.conversation;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder(toBuilder = true)
@AllArgsConstructor
public class Message {

    private final UUID id;
    private final UUID conversationId;
    private final MessageRole role;
    private final String content;
    private final List<Citation> citations;
    private final Instant createdAt;
}
