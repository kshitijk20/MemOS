package com.memos.infrastructure.persistence.adapter;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.memos.domain.collection.Collection;
import com.memos.domain.collection.CollectionRepositoryPort;
import com.memos.infrastructure.persistence.mapper.CollectionMapper;
import com.memos.infrastructure.persistence.repository.CollectionJpaRepository;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class CollectionRepositoryAdapter implements CollectionRepositoryPort {

    private final CollectionJpaRepository jpaRepository;
    private final CollectionMapper mapper;

    @Override
    @Transactional
    public Collection save(Collection collection) {
        return mapper.toDomain(jpaRepository.save(mapper.toEntity(collection)));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Collection> findByIdAndOwnerId(UUID id, UUID ownerId) {
        return jpaRepository.findByIdAndOwnerId(id, ownerId).map(mapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Collection> findAllByOwnerId(UUID ownerId) {
        return jpaRepository.findAllByOwnerIdOrderByNameAsc(ownerId).stream().map(mapper::toDomain).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsByOwnerIdAndNameIgnoreCase(UUID ownerId, String name) {
        return jpaRepository.existsByOwnerIdAndNameIgnoreCase(ownerId, name);
    }

    @Override
    @Transactional
    public void deleteById(UUID id) {
        jpaRepository.deleteById(id);
    }
}
