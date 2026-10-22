package com.memos.infrastructure.persistence.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.memos.infrastructure.persistence.entity.CollectionEntity;

public interface CollectionJpaRepository extends JpaRepository<CollectionEntity, UUID> {

    Optional<CollectionEntity> findByIdAndOwnerId(UUID id, UUID ownerId);

    List<CollectionEntity> findAllByOwnerIdOrderByNameAsc(UUID ownerId);

    boolean existsByOwnerIdAndNameIgnoreCase(UUID ownerId, String name);
}
