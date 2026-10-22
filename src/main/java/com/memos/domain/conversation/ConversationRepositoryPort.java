package com.memos.domain.conversation;

import java.util.Optional;
import java.util.UUID;

import com.memos.domain.common.PageRequest;
import com.memos.domain.common.PageResult;

public interface ConversationRepositoryPort {

    Conversation save(Conversation conversation);

    Optional<Conversation> findById(UUID id);

    Optional<Conversation> findByIdAndUserId(UUID id, UUID userId);

    PageResult<Conversation> findAllByUserId(UUID userId, PageRequest pageRequest);

    void deleteById(UUID id);

    boolean existsByIdAndUserId(UUID id, UUID userId);

    long countByUserId(UUID userId);
}
