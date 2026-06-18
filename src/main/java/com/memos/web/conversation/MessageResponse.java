package com.memos.web.conversation;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.memos.domain.conversation.Message;
import com.memos.domain.conversation.MessageRole;

public record MessageResponse(
        UUID id,
        MessageRole role,
        String content,
        List<CitationResponse> citations,
        Instant createdAt) {

    public static MessageResponse from(Message message) {
        List<CitationResponse> citations = message.getCitations() == null
                ? List.of()
                : message.getCitations().stream().map(CitationResponse::from).toList();

        return new MessageResponse(
                message.getId(),
                message.getRole(),
                message.getContent(),
                citations,
                message.getCreatedAt());
    }
}
