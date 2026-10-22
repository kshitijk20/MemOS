package com.memos.infrastructure.persistence.adapter;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.memos.domain.common.PageRequest;
import com.memos.domain.common.PageResult;
import com.memos.domain.document.DocumentChunk;
import com.memos.domain.document.DocumentChunkRepositoryPort;
import com.memos.infrastructure.persistence.PageMappingUtil;
import com.memos.infrastructure.persistence.mapper.DocumentChunkMapper;
import com.memos.infrastructure.persistence.repository.DocumentChunkJpaRepository;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class DocumentChunkRepositoryAdapter implements DocumentChunkRepositoryPort {

    private final DocumentChunkJpaRepository jpaRepository;
    private final DocumentChunkMapper mapper;

    @Override
    public DocumentChunk save(DocumentChunk chunk) {
        return mapper.toDomain(jpaRepository.save(mapper.toEntity(chunk)));
    }

    @Override
    @Transactional
    public List<DocumentChunk> saveAll(List<DocumentChunk> chunks) {
        return jpaRepository.saveAll(chunks.stream().map(mapper::toEntity).toList())
                .stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<DocumentChunk> findByDocumentId(UUID documentId, PageRequest pageRequest) {
        return PageMappingUtil.toPageResult(
                jpaRepository.findByDocumentId(documentId, PageMappingUtil.toPageable(pageRequest)),
                mapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<DocumentChunk> findByIdAndDocumentId(UUID chunkId, UUID documentId) {
        return jpaRepository.findByIdAndDocumentId(chunkId, documentId).map(mapper::toDomain);
    }

    @Override
    @Transactional
    public void deleteByDocumentId(UUID documentId) {
        jpaRepository.deleteByDocumentId(documentId);
    }

    @Override
    @Transactional
    public List<DocumentChunk> replaceForDocument(UUID documentId, List<DocumentChunk> chunks) {
        jpaRepository.deleteByDocumentId(documentId);
        return saveAll(chunks);
    }

    @Override
    @Transactional(readOnly = true)
    public long countByDocumentId(UUID documentId) {
        return jpaRepository.countByDocumentId(documentId);
    }
}
