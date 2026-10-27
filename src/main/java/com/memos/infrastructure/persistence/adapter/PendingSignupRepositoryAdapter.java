package com.memos.infrastructure.persistence.adapter;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.memos.domain.user.PendingSignup;
import com.memos.domain.user.PendingSignupRepositoryPort;
import com.memos.infrastructure.persistence.mapper.PendingSignupMapper;
import com.memos.infrastructure.persistence.repository.PendingSignupJpaRepository;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class PendingSignupRepositoryAdapter implements PendingSignupRepositoryPort {

    private final PendingSignupJpaRepository jpaRepository;
    private final PendingSignupMapper mapper;

    @Override
    @Transactional
    public PendingSignup replace(PendingSignup signup) {
        jpaRepository.deleteByEmailKey(signup.getEmailKey());
        jpaRepository.flush();
        return mapper.toDomain(jpaRepository.save(mapper.toEntity(signup.toBuilder().id(null).build())));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<PendingSignup> findByEmailKey(String emailKey) {
        return jpaRepository.findByEmailKey(emailKey).map(mapper::toDomain);
    }

    @Override
    @Transactional
    public void recordFailedAttempt(UUID id) {
        jpaRepository.incrementAttempts(id);
    }

    @Override
    @Transactional
    public boolean delete(UUID id) {
        return jpaRepository.deleteAndCount(id) == 1;
    }

    @Override
    @Transactional
    public void deleteExpired(Instant now) {
        jpaRepository.deleteExpired(now);
    }
}
