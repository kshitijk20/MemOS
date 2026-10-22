package com.memos.domain.conversation;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.memos.domain.common.PageRequest;
import com.memos.domain.common.PageResult;

public interface MessageRepositoryPort {

    /**
     * Persists a message and, if present, its citations.
     */
    Message save(Message message);

    PageResult<Message> findByConversationId(UUID conversationId, PageRequest pageRequest);

    /**
     * Returns the most recent {@code limit} messages for a conversation, ordered oldest
     * first, for use as chat history context in the RAG prompt.
     */
    List<Message> findRecentByConversationId(UUID conversationId, int limit);

    long countByConversationId(UUID conversationId);

    Optional<Message> findByIdAndConversationId(UUID id, UUID conversationId);

    /** Sets or clears ({@code feedback == null}) a message's rating without touching anything else. */
    void updateFeedback(UUID messageId, MessageFeedback feedback, String comment, Instant feedbackAt);

    /** Rated answers in the user's conversations, newest rating first; {@code feedback == null} returns both kinds. */
    PageResult<FeedbackEntry> findFeedbackByUserId(UUID userId, MessageFeedback feedback, PageRequest pageRequest);

    long countFeedbackByUserId(UUID userId, MessageFeedback feedback);
}
