package com.memos.infrastructure.persistence.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.memos.infrastructure.persistence.entity.ConversationEntity;

public interface ConversationJpaRepository extends JpaRepository<ConversationEntity, UUID> {

    Optional<ConversationEntity> findByIdAndUserId(UUID id, UUID userId);

    Page<ConversationEntity> findAllByUserId(UUID userId, Pageable pageable);

    boolean existsByIdAndUserId(UUID id, UUID userId);
}
