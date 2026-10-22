package com.memos.domain.conversation;

import java.util.List;
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
}
