package com.memos.web.conversation;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.memos.domain.conversation.Message;
import com.memos.domain.conversation.MessageFeedback;
import com.memos.domain.conversation.MessageRole;

public record MessageResponse(
        UUID id,
        MessageRole role,
        String content,
        List<CitationResponse> citations,
        Instant createdAt,
        boolean unanswered,
        String searchQuery,
        MessageFeedback feedback,
        String feedbackComment) {

    public static MessageResponse from(Message message) {
        List<CitationResponse> citations = message.getCitations() == null
                ? List.of()
                : message.getCitations().stream().map(CitationResponse::from).toList();

        return new MessageResponse(
                message.getId(),
                message.getRole(),
                message.getContent(),
                citations,
                message.getCreatedAt(),
                message.isUnanswered(),
                message.getSearchQuery(),
                message.getFeedback(),
                message.getFeedbackComment());
    }
}
