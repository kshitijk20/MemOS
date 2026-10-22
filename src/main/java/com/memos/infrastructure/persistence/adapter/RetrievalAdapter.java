package com.memos.infrastructure.persistence.adapter;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.memos.application.rag.ReciprocalRankFusion;
import com.memos.application.rag.RetrievalPort;
import com.memos.application.rag.RetrievedChunk;
import com.memos.application.rag.SearchQueryTerms;
import com.memos.infrastructure.persistence.repository.DocumentChunkJpaRepository;
import com.memos.infrastructure.persistence.repository.SimilarChunkProjection;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class RetrievalAdapter implements RetrievalPort {

    private final DocumentChunkJpaRepository jpaRepository;

    @Value("${memos.rag.rrf-k:60}")
    private int rrfK;

    @Override
    public List<RetrievedChunk> findSimilar(UUID ownerId, float[] queryEmbedding, int topK) {
        String queryVector = toVectorLiteral(queryEmbedding);
        return jpaRepository.findSimilarByOwner(ownerId, queryVector, topK).stream()
                .map(this::toVectorChunk)
                .toList();
    }

    @Override
    public List<RetrievedChunk> findSimilarInScope(UUID ownerId, float[] queryEmbedding, List<UUID> documentScope, int topK) {
        String queryVector = toVectorLiteral(queryEmbedding);
        return jpaRepository.findSimilarByOwnerAndDocuments(ownerId, queryVector, documentScope, topK).stream()
                .map(this::toVectorChunk)
                .toList();
    }

    @Override
    public List<RetrievedChunk> findHybrid(
            UUID ownerId,
            String query,
            float[] queryEmbedding,
            List<UUID> documentScope,
            int limit) {
        boolean scoped = documentScope != null && !documentScope.isEmpty();
        List<RetrievedChunk> vectorHits = scoped
                ? findSimilarInScope(ownerId, queryEmbedding, documentScope, limit)
                : findSimilar(ownerId, queryEmbedding, limit);

        String trimmed = query == null ? "" : query.trim();
        if (trimmed.isEmpty()) {
            return vectorHits;
        }

        String patternsCsv = SearchQueryTerms.identifiers(trimmed).stream()
                .map(SearchQueryTerms::escapeLike)
                .collect(Collectors.joining("|||"));

        List<SimilarChunkProjection> keywordProjections = scoped
                ? jpaRepository.findKeywordByOwnerAndDocuments(ownerId, trimmed, patternsCsv, documentScope, limit)
                : jpaRepository.findKeywordByOwner(ownerId, trimmed, patternsCsv, limit);

        List<RetrievedChunk> keywordHits = keywordProjections.stream()
                .map(this::toKeywordChunk)
                .toList();

        if (keywordHits.isEmpty()) {
            return vectorHits;
        }
        return ReciprocalRankFusion.fuse(vectorHits, keywordHits, rrfK);
    }

    private RetrievedChunk toVectorChunk(SimilarChunkProjection projection) {
        return new RetrievedChunk(
                projection.getId(),
                projection.getDocumentId(),
                projection.getChunkIndex(),
                projection.getContent(),
                projection.getSourceTitle(),
                projection.getScore(),
                projection.getScore(),
                false);
    }

    private RetrievedChunk toKeywordChunk(SimilarChunkProjection projection) {
        return new RetrievedChunk(
                projection.getId(),
                projection.getDocumentId(),
                projection.getChunkIndex(),
                projection.getContent(),
                projection.getSourceTitle(),
                projection.getScore(),
                0.0,
                true);
    }

    private String toVectorLiteral(float[] embedding) {
        StringBuilder builder = new StringBuilder(embedding.length * 10);
        builder.append('[');
        for (int i = 0; i < embedding.length; i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append(embedding[i]);
        }
        builder.append(']');
        return builder.toString();
    }
}
