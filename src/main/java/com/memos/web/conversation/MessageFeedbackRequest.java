package com.memos.web.conversation;

import com.memos.domain.conversation.MessageFeedback;

import jakarta.validation.constraints.Size;

/** {@code feedback} null clears the rating. */
public record MessageFeedbackRequest(
        MessageFeedback feedback,
        @Size(max = 2000)
        String comment) {
}
