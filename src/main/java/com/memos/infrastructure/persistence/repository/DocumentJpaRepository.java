package com.memos.infrastructure.persistence.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.memos.domain.document.DocumentStatus;
import com.memos.infrastructure.persistence.entity.DocumentEntity;

public interface DocumentJpaRepository extends JpaRepository<DocumentEntity, UUID> {

    Optional<DocumentEntity> findByIdAndOwnerId(UUID id, UUID ownerId);

    Page<DocumentEntity> findAllByOwnerId(UUID ownerId, Pageable pageable);

    boolean existsByIdAndOwnerId(UUID id, UUID ownerId);

    long countByOwnerId(UUID ownerId);

    long countByOwnerIdAndStatus(UUID ownerId, DocumentStatus status);

    Optional<DocumentEntity> findByOwnerIdAndSourceFingerprint(UUID ownerId, String sourceFingerprint);

    List<DocumentEntity> findAllByStatus(DocumentStatus status);

    List<DocumentEntity> findAllBySourceFingerprintIsNull();
}
