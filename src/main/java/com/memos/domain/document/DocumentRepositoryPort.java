package com.memos.domain.document;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.memos.domain.common.PageRequest;
import com.memos.domain.common.PageResult;

public interface DocumentRepositoryPort {

    Document save(Document document);

    Optional<Document> findById(UUID id);

    Optional<Document> findByIdAndOwnerId(UUID id, UUID ownerId);

    PageResult<Document> findAllByOwnerId(UUID ownerId, PageRequest pageRequest);

    void deleteById(UUID id);

    boolean existsByIdAndOwnerId(UUID id, UUID ownerId);

    long countByOwnerId(UUID ownerId);

    long countByOwnerIdAndStatus(UUID ownerId, DocumentStatus status);

    Optional<Document> findByOwnerIdAndSourceFingerprint(UUID ownerId, String sourceFingerprint);

    List<Document> findAllByStatus(DocumentStatus status);

    List<Document> findAllBySourceFingerprintIsNull();
}
