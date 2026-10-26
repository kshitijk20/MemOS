package com.memos.infrastructure.persistence.adapter;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.memos.domain.user.PasswordResetToken;
import com.memos.domain.user.PasswordResetTokenRepositoryPort;
import com.memos.infrastructure.persistence.mapper.PasswordResetTokenMapper;
import com.memos.infrastructure.persistence.repository.PasswordResetTokenJpaRepository;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class PasswordResetTokenRepositoryAdapter implements PasswordResetTokenRepositoryPort {

    private final PasswordResetTokenJpaRepository jpaRepository;
    private final PasswordResetTokenMapper mapper;

    @Override
    @Transactional
    public PasswordResetToken save(PasswordResetToken token) {
        return mapper.toDomain(jpaRepository.save(mapper.toEntity(token)));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<PasswordResetToken> findByTokenHash(String tokenHash) {
        return jpaRepository.findByTokenHash(tokenHash).map(mapper::toDomain);
    }

    @Override
    @Transactional
    public void invalidateUnusedForUser(UUID userId, Instant now) {
        jpaRepository.invalidateUnusedForUser(userId, now);
    }

    @Override
    @Transactional
    public boolean markUsed(UUID tokenId, Instant now) {
        return jpaRepository.markUsed(tokenId, now) == 1;
    }
}
