package com.memos.infrastructure.security;

import java.time.Instant;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.memos.infrastructure.persistence.repository.RefreshTokenJpaRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Nightly job that purges refresh tokens that are either expired or explicitly revoked.
 * Without this, the table would grow unboundedly as users log in over time.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RefreshTokenCleanupScheduler {

    private final RefreshTokenJpaRepository refreshTokenJpaRepository;

    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void purgeExpiredAndRevokedTokens() {
        int deleted = refreshTokenJpaRepository.deleteExpiredOrRevoked(Instant.now());
        if (deleted > 0) {
            log.info("Purged {} expired or revoked refresh tokens", deleted);
        }
    }
}
