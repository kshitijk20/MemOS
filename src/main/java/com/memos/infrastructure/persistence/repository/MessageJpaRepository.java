package com.memos.infrastructure.persistence.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.memos.infrastructure.persistence.entity.MessageEntity;

public interface MessageJpaRepository extends JpaRepository<MessageEntity, UUID> {

    Page<MessageEntity> findByConversationIdOrderByCreatedAtAsc(UUID conversationId, Pageable pageable);

    @Query("""
            SELECT m FROM MessageEntity m
            WHERE m.conversationId = :conversationId
            ORDER BY m.createdAt DESC
            """)
    List<MessageEntity> findRecentByConversationId(@Param("conversationId") UUID conversationId, Pageable pageable);

    long countByConversationId(UUID conversationId);
}
