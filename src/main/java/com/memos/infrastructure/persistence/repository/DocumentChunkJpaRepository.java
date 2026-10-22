package com.memos.infrastructure.persistence.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.memos.infrastructure.persistence.entity.DocumentChunkEntity;

public interface DocumentChunkJpaRepository extends JpaRepository<DocumentChunkEntity, UUID> {

    Page<DocumentChunkEntity> findByDocumentId(UUID documentId, Pageable pageable);

    Optional<DocumentChunkEntity> findByIdAndDocumentId(UUID id, UUID documentId);

    Optional<DocumentChunkEntity> findByDocumentIdAndChunkIndex(UUID documentId, int chunkIndex);

    /** Single bulk DELETE; the derived form would load every chunk (and its vector) first. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM DocumentChunkEntity c WHERE c.documentId = :documentId")
    void deleteByDocumentId(@Param("documentId") UUID documentId);

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
              AND c.document_id IN (:documentIds)
              AND d.status = 'COMPLETED'
            ORDER BY c.embedding <=> CAST(:queryVector AS vector)
            LIMIT :topK
            """, nativeQuery = true)
    List<SimilarChunkProjection> findSimilarByOwnerAndDocuments(
            @Param("ownerId") UUID ownerId,
            @Param("queryVector") String queryVector,
            @Param("documentIds") List<UUID> documentIds,
            @Param("topK") int topK);

    @Query(value = """
            SELECT c.id AS id,
                   c.document_id AS documentId,
                   c.chunk_index AS chunkIndex,
                   c.content AS content,
                   d.title AS sourceTitle,
                   ts_rank_cd(c.content_tsv, plainto_tsquery('simple', :query)) AS score
            FROM document_chunks c
            JOIN documents d ON d.id = c.document_id
            WHERE d.owner_id = :ownerId
              AND d.status = 'COMPLETED'
              AND (
                    c.content_tsv @@ plainto_tsquery('simple', :query)
                    OR (
                        :patternsCsv <> ''
                        AND EXISTS (
                            SELECT 1
                            FROM unnest(string_to_array(:patternsCsv, '|||')) AS p
                            WHERE length(p) > 0
                              AND c.content ILIKE ('%' || p || '%') ESCAPE '\\'
                        )
                    )
              )
            ORDER BY ts_rank_cd(c.content_tsv, plainto_tsquery('simple', :query)) DESC
            LIMIT :topK
            """, nativeQuery = true)
    List<SimilarChunkProjection> findKeywordByOwner(
            @Param("ownerId") UUID ownerId,
            @Param("query") String query,
            @Param("patternsCsv") String patternsCsv,
            @Param("topK") int topK);

    @Query(value = """
            SELECT c.id AS id,
                   c.document_id AS documentId,
                   c.chunk_index AS chunkIndex,
                   c.content AS content,
                   d.title AS sourceTitle,
                   ts_rank_cd(c.content_tsv, plainto_tsquery('simple', :query)) AS score
            FROM document_chunks c
            JOIN documents d ON d.id = c.document_id
            WHERE d.owner_id = :ownerId
              AND c.document_id IN (:documentIds)
              AND d.status = 'COMPLETED'
              AND (
                    c.content_tsv @@ plainto_tsquery('simple', :query)
                    OR (
                        :patternsCsv <> ''
                        AND EXISTS (
                            SELECT 1
                            FROM unnest(string_to_array(:patternsCsv, '|||')) AS p
                            WHERE length(p) > 0
                              AND c.content ILIKE ('%' || p || '%') ESCAPE '\\'
                        )
                    )
              )
            ORDER BY ts_rank_cd(c.content_tsv, plainto_tsquery('simple', :query)) DESC
            LIMIT :topK
            """, nativeQuery = true)
    List<SimilarChunkProjection> findKeywordByOwnerAndDocuments(
            @Param("ownerId") UUID ownerId,
            @Param("query") String query,
            @Param("patternsCsv") String patternsCsv,
            @Param("documentIds") List<UUID> documentIds,
            @Param("topK") int topK);
}
