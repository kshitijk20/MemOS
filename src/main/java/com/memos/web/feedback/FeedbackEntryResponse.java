package com.memos.web.feedback;

import java.time.Instant;
import java.util.UUID;

import com.memos.domain.conversation.FeedbackEntry;
import com.memos.domain.conversation.MessageFeedback;

public record FeedbackEntryResponse(
        UUID messageId,
        UUID conversationId,
        String conversationTitle,
        String question,
        String answer,
        MessageFeedback feedback,
        String comment,
        Instant feedbackAt) {

    public static FeedbackEntryResponse from(FeedbackEntry entry) {
        return new FeedbackEntryResponse(
                entry.messageId(),
                entry.conversationId(),
                entry.conversationTitle(),
                entry.question(),
                entry.answer(),
                entry.feedback(),
                entry.comment(),
                entry.feedbackAt());
    }
}
