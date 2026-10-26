package com.memos.infrastructure.persistence.repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.memos.infrastructure.persistence.entity.PasswordResetTokenEntity;

public interface PasswordResetTokenJpaRepository extends JpaRepository<PasswordResetTokenEntity, UUID> {

    Optional<PasswordResetTokenEntity> findByTokenHash(String tokenHash);

    @Modifying
    @Query("UPDATE PasswordResetTokenEntity t SET t.usedAt = :now WHERE t.userId = :userId AND t.usedAt IS NULL")
    int invalidateUnusedForUser(@Param("userId") UUID userId, @Param("now") Instant now);

    /** Conditional update, so two concurrent requests can't both consume the same token. */
    @Modifying
    @Query("UPDATE PasswordResetTokenEntity t SET t.usedAt = :now WHERE t.id = :id AND t.usedAt IS NULL")
    int markUsed(@Param("id") UUID id, @Param("now") Instant now);
}
