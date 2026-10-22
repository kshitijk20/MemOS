package com.memos.application.collection;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.memos.domain.collection.Collection;
import com.memos.domain.collection.CollectionRepositoryPort;
import com.memos.domain.document.DocumentRepositoryPort;
import com.memos.domain.exception.DuplicateResourceException;
import com.memos.domain.exception.InvalidRequestException;
import com.memos.domain.exception.ResourceNotFoundException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CollectionApplicationService {

    private final CollectionRepositoryPort collectionRepositoryPort;
    private final DocumentRepositoryPort documentRepositoryPort;

    public List<Collection> list(UUID userId) {
        return collectionRepositoryPort.findAllByOwnerId(userId);
    }

    public Collection get(UUID userId, UUID collectionId) {
        return collectionRepositoryPort.findByIdAndOwnerId(collectionId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Collection", collectionId));
    }

    public Collection create(UUID userId, String name, List<UUID> documentIds) {
        String normalized = normalizeName(name);
        if (collectionRepositoryPort.existsByOwnerIdAndNameIgnoreCase(userId, normalized)) {
            throw new DuplicateResourceException("You already have a collection named \"" + normalized + "\".");
        }
        Instant now = Instant.now();
        return collectionRepositoryPort.save(Collection.builder()
                .ownerId(userId)
                .name(normalized)
                .documentIds(ownedDocumentIds(userId, documentIds))
                .createdAt(now)
                .updatedAt(now)
                .build());
    }

    /** Renames and/or replaces the document list; {@code null} leaves that part unchanged. */
    public Collection update(UUID userId, UUID collectionId, String name, List<UUID> documentIds) {
        Collection collection = get(userId, collectionId);
        Collection.CollectionBuilder updated = collection.toBuilder().updatedAt(Instant.now());

        if (name != null) {
            String normalized = normalizeName(name);
            if (!normalized.equalsIgnoreCase(collection.getName())
                    && collectionRepositoryPort.existsByOwnerIdAndNameIgnoreCase(userId, normalized)) {
                throw new DuplicateResourceException("You already have a collection named \"" + normalized + "\".");
            }
            updated.name(normalized);
        }
        if (documentIds != null) {
            updated.documentIds(ownedDocumentIds(userId, documentIds));
        }
        return collectionRepositoryPort.save(updated.build());
    }

    public void delete(UUID userId, UUID collectionId) {
        collectionRepositoryPort.deleteById(get(userId, collectionId).getId());
    }

    private Set<UUID> ownedDocumentIds(UUID userId, List<UUID> documentIds) {
        Set<UUID> ids = documentIds == null ? new HashSet<>() : new HashSet<>(documentIds);
        for (UUID documentId : ids) {
            if (!documentRepositoryPort.existsByIdAndOwnerId(documentId, userId)) {
                throw new ResourceNotFoundException("Document", documentId);
            }
        }
        return ids;
    }

    private static String normalizeName(String name) {
        String normalized = name == null ? "" : name.strip().replaceAll("\\s+", " ");
        if (normalized.isEmpty()) {
            throw new InvalidRequestException("Collection name is required.");
        }
        if (normalized.length() > Collection.MAX_NAME_LENGTH) {
            throw new InvalidRequestException(
                    "Collection name must be at most " + Collection.MAX_NAME_LENGTH + " characters.");
        }
        return normalized;
    }
}
