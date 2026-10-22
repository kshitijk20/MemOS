package com.memos.domain.conversation;

import java.time.Instant;
import java.util.UUID;

/** A rated assistant answer together with the question that produced it, for review. */
public record FeedbackEntry(
        UUID messageId,
        UUID conversationId,
        String conversationTitle,
        String question,
        String answer,
        MessageFeedback feedback,
        String comment,
        Instant feedbackAt) {
}
