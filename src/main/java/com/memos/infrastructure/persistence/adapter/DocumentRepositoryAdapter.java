package com.memos.infrastructure.persistence.adapter;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.memos.domain.common.PageResult;
import com.memos.domain.document.Document;
import com.memos.domain.document.DocumentRepositoryPort;
import com.memos.domain.document.DocumentStatus;
import com.memos.infrastructure.persistence.PageMappingUtil;
import com.memos.infrastructure.persistence.mapper.DocumentMapper;
import com.memos.infrastructure.persistence.repository.DocumentJpaRepository;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class DocumentRepositoryAdapter implements DocumentRepositoryPort {

    private final DocumentJpaRepository jpaRepository;
    private final DocumentMapper mapper;

    @Override
    public Document save(Document document) {
        return mapper.toDomain(jpaRepository.save(mapper.toEntity(document)));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Document> findById(UUID id) {
        return jpaRepository.findById(id).map(mapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Document> findByIdAndOwnerId(UUID id, UUID ownerId) {
        return jpaRepository.findByIdAndOwnerId(id, ownerId).map(mapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<Document> findAllByOwnerId(UUID ownerId, com.memos.domain.common.PageRequest pageRequest) {
        return PageMappingUtil.toPageResult(
                jpaRepository.findAllByOwnerId(ownerId, PageMappingUtil.toPageable(pageRequest)),
                mapper::toDomain);
    }

    @Override
    public void deleteById(UUID id) {
        jpaRepository.deleteById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsByIdAndOwnerId(UUID id, UUID ownerId) {
        return jpaRepository.existsByIdAndOwnerId(id, ownerId);
    }

    @Override
    @Transactional(readOnly = true)
    public long countByOwnerId(UUID ownerId) {
        return jpaRepository.countByOwnerId(ownerId);
    }

    @Override
    @Transactional(readOnly = true)
    public long countByOwnerIdAndStatus(UUID ownerId, DocumentStatus status) {
        return jpaRepository.countByOwnerIdAndStatus(ownerId, status);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Document> findByOwnerIdAndSourceFingerprint(UUID ownerId, String sourceFingerprint) {
        return jpaRepository.findByOwnerIdAndSourceFingerprint(ownerId, sourceFingerprint).map(mapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Document> findAllByStatus(DocumentStatus status) {
        return jpaRepository.findAllByStatus(status).stream().map(mapper::toDomain).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Document> findAllBySourceFingerprintIsNull() {
        return jpaRepository.findAllBySourceFingerprintIsNull().stream().map(mapper::toDomain).toList();
    }
}
