package com.memos.infrastructure.persistence.repository;

import java.time.Instant;
import java.util.UUID;

public interface FeedbackEntryProjection {

    UUID getMessageId();

    UUID getConversationId();

    String getConversationTitle();

    String getQuestion();

    String getAnswer();

    String getFeedback();

    String getComment();

    Instant getFeedbackAt();
}
