package com.memos.domain.collection;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CollectionRepositoryPort {

    Collection save(Collection collection);

    Optional<Collection> findByIdAndOwnerId(UUID id, UUID ownerId);

    /** All of the owner's collections, ordered by name. */
    List<Collection> findAllByOwnerId(UUID ownerId);

    boolean existsByOwnerIdAndNameIgnoreCase(UUID ownerId, String name);

    void deleteById(UUID id);
}
