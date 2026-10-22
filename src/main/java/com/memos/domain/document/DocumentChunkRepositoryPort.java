package com.memos.domain.document;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.memos.domain.common.PageRequest;
import com.memos.domain.common.PageResult;

public interface DocumentChunkRepositoryPort {

    DocumentChunk save(DocumentChunk chunk);

    List<DocumentChunk> saveAll(List<DocumentChunk> chunks);

    PageResult<DocumentChunk> findByDocumentId(UUID documentId, PageRequest pageRequest);

    Optional<DocumentChunk> findByIdAndDocumentId(UUID chunkId, UUID documentId);

    void deleteByDocumentId(UUID documentId);

    /**
     * Atomically swaps a document's chunks: the old chunks are deleted and the new ones saved in
     * one transaction, so a failed save leaves the previous version searchable.
     */
    List<DocumentChunk> replaceForDocument(UUID documentId, List<DocumentChunk> chunks);

    long countByDocumentId(UUID documentId);
}
