package com.memos.web.conversation;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.memos.domain.ai.AiProvider;
import com.memos.domain.conversation.Conversation;

public record ConversationResponse(
        UUID id,
        String title,
        AiProvider provider,
        String model,
        List<UUID> documentScope,
        Instant createdAt,
        Instant updatedAt) {

    public static ConversationResponse from(Conversation conversation) {
        return new ConversationResponse(
                conversation.getId(),
                conversation.getTitle(),
                conversation.getProvider(),
                conversation.getModel(),
                conversation.getDocumentScope() != null ? conversation.getDocumentScope() : List.of(),
                conversation.getCreatedAt(),
                conversation.getUpdatedAt());
    }
}
