package com.memos.infrastructure.persistence.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.memos.infrastructure.persistence.entity.DocumentChunkEntity;

public interface DocumentChunkJpaRepository extends JpaRepository<DocumentChunkEntity, UUID> {

    Page<DocumentChunkEntity> findByDocumentId(UUID documentId, Pageable pageable);

    void deleteByDocumentId(UUID documentId);

    long countByDocumentId(UUID documentId);

    /**
     * Vector similarity search restricted to documents owned by {@code ownerId}, ordered by
     * cosine distance ascending (most similar first). {@code queryVector} must be a pgvector
     * literal string, e.g. {@code "[0.1,0.2,...]"}.
     */
    @Query(value = """
            SELECT c.id AS id,
                   c.document_id AS documentId,
                   c.chunk_index AS chunkIndex,
                   c.content AS content,
                   d.title AS sourceTitle,
                   (1 - (c.embedding <=> CAST(:queryVector AS vector))) AS score
            FROM document_chunks c
            JOIN documents d ON d.id = c.document_id
            WHERE d.owner_id = :ownerId
              AND d.status = 'COMPLETED'
            ORDER BY c.embedding <=> CAST(:queryVector AS vector)
            LIMIT :topK
            """, nativeQuery = true)
    List<SimilarChunkProjection> findSimilarByOwner(
            @Param("ownerId") UUID ownerId,
            @Param("queryVector") String queryVector,
            @Param("topK") int topK);
}
