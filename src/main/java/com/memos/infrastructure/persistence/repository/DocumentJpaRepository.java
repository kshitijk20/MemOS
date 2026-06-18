package com.memos.infrastructure.persistence.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.memos.infrastructure.persistence.entity.DocumentEntity;

public interface DocumentJpaRepository extends JpaRepository<DocumentEntity, UUID> {

    Optional<DocumentEntity> findByIdAndOwnerId(UUID id, UUID ownerId);

    Page<DocumentEntity> findAllByOwnerId(UUID ownerId, Pageable pageable);

    boolean existsByIdAndOwnerId(UUID id, UUID ownerId);
}
