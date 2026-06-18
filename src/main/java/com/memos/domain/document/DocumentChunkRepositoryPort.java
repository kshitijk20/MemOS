package com.memos.domain.document;

import java.util.List;
import java.util.UUID;

import com.memos.domain.common.PageRequest;
import com.memos.domain.common.PageResult;

public interface DocumentChunkRepositoryPort {

    DocumentChunk save(DocumentChunk chunk);

    List<DocumentChunk> saveAll(List<DocumentChunk> chunks);

    PageResult<DocumentChunk> findByDocumentId(UUID documentId, PageRequest pageRequest);

    void deleteByDocumentId(UUID documentId);

    long countByDocumentId(UUID documentId);
}
