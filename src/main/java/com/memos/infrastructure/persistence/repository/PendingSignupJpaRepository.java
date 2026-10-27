package com.memos.infrastructure.persistence.repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.memos.infrastructure.persistence.entity.PendingSignupEntity;

public interface PendingSignupJpaRepository extends JpaRepository<PendingSignupEntity, UUID> {

    Optional<PendingSignupEntity> findByEmailKey(String emailKey);

    @Modifying
    @Query("DELETE FROM PendingSignupEntity s WHERE s.emailKey = :emailKey")
    int deleteByEmailKey(@Param("emailKey") String emailKey);

    @Modifying
    @Query("UPDATE PendingSignupEntity s SET s.attempts = s.attempts + 1 WHERE s.id = :id")
    int incrementAttempts(@Param("id") UUID id);

    /** Conditional delete, so two concurrent verifications can't both create the account. */
    @Modifying
    @Query("DELETE FROM PendingSignupEntity s WHERE s.id = :id")
    int deleteAndCount(@Param("id") UUID id);

    @Modifying
    @Query("DELETE FROM PendingSignupEntity s WHERE s.expiresAt < :now")
    int deleteExpired(@Param("now") Instant now);
}
