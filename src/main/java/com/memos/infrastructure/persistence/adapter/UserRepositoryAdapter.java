package com.memos.infrastructure.persistence.adapter;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.memos.domain.user.User;
import com.memos.domain.user.UserRepositoryPort;
import com.memos.infrastructure.persistence.mapper.UserMapper;
import com.memos.infrastructure.persistence.repository.UserJpaRepository;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class UserRepositoryAdapter implements UserRepositoryPort {

    private final UserJpaRepository jpaRepository;
    private final UserMapper mapper;

    @Override
    public User save(User user) {
        return mapper.toDomain(jpaRepository.save(mapper.toEntity(user)));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<User> findById(UUID id) {
        return jpaRepository.findById(id).map(mapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<User> findByEmail(String email) {
        return jpaRepository.findByEmail(email).map(mapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsByEmail(String email) {
        return jpaRepository.existsByEmail(email);
    }

    @Override
    @Transactional
    public void deleteById(UUID id) {
        jpaRepository.deleteById(id);
    }
}
