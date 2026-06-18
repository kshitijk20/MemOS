package com.memos.infrastructure.persistence.adapter;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.memos.application.rag.RetrievalPort;
import com.memos.application.rag.RetrievedChunk;
import com.memos.infrastructure.persistence.repository.DocumentChunkJpaRepository;
import com.memos.infrastructure.persistence.repository.SimilarChunkProjection;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class RetrievalAdapter implements RetrievalPort {

    private final DocumentChunkJpaRepository jpaRepository;

    @Override
    public List<RetrievedChunk> findSimilar(UUID ownerId, float[] queryEmbedding, int topK) {
        String queryVector = toVectorLiteral(queryEmbedding);
        return jpaRepository.findSimilarByOwner(ownerId, queryVector, topK).stream()
                .map(this::toRetrievedChunk)
                .toList();
    }

    private RetrievedChunk toRetrievedChunk(SimilarChunkProjection projection) {
        return new RetrievedChunk(
                projection.getId(),
                projection.getDocumentId(),
                projection.getChunkIndex(),
                projection.getContent(),
                projection.getSourceTitle(),
                projection.getScore());
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
