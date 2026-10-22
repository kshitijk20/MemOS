package com.memos.infrastructure.persistence.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.memos.domain.conversation.MessageFeedback;
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

    Optional<MessageEntity> findByIdAndConversationId(UUID id, UUID conversationId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            UPDATE MessageEntity m
            SET m.feedback = :feedback, m.feedbackComment = :comment, m.feedbackAt = :feedbackAt
            WHERE m.id = :id
            """)
    void updateFeedback(
            @Param("id") UUID id,
            @Param("feedback") MessageFeedback feedback,
            @Param("comment") String comment,
            @Param("feedbackAt") Instant feedbackAt);

    /**
     * Rated assistant answers in the user's conversations, each paired with the most recent user
     * question asked before it. {@code feedback} null matches both ratings.
     */
    @Query(value = """
            SELECT m.id AS messageId,
                   m.conversation_id AS conversationId,
                   c.title AS conversationTitle,
                   (SELECT q.content FROM messages q
                     WHERE q.conversation_id = m.conversation_id
                       AND q.role = 'USER'
                       AND q.created_at <= m.created_at
                     ORDER BY q.created_at DESC
                     LIMIT 1) AS question,
                   m.content AS answer,
                   m.feedback AS feedback,
                   m.feedback_comment AS comment,
                   m.feedback_at AS feedbackAt
            FROM messages m
            JOIN conversations c ON c.id = m.conversation_id
            WHERE c.user_id = :userId
              AND m.feedback IS NOT NULL
              AND (CAST(:feedback AS varchar) IS NULL OR m.feedback = CAST(:feedback AS varchar))
            ORDER BY m.feedback_at DESC
            """,
            countQuery = """
            SELECT count(*)
            FROM messages m
            JOIN conversations c ON c.id = m.conversation_id
            WHERE c.user_id = :userId
              AND m.feedback IS NOT NULL
              AND (CAST(:feedback AS varchar) IS NULL OR m.feedback = CAST(:feedback AS varchar))
            """,
            nativeQuery = true)
    Page<FeedbackEntryProjection> findFeedbackByUserId(
            @Param("userId") UUID userId,
            @Param("feedback") String feedback,
            Pageable pageable);

    @Query("""
            SELECT count(m) FROM MessageEntity m, ConversationEntity c
            WHERE c.id = m.conversationId AND c.userId = :userId AND m.feedback = :feedback
            """)
    long countFeedbackByUserId(@Param("userId") UUID userId, @Param("feedback") MessageFeedback feedback);
}
