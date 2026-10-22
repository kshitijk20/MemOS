package com.memos.application.rag;

import java.util.UUID;

/**
 * A document chunk returned by retrieval. {@code score} is the ranking score used to order
 * context (RRF then rerank). {@code vectorScore} is cosine similarity. {@code lexicalHit}
 * is true when the keyword/identifier path matched this chunk.
 */
public record RetrievedChunk(
        UUID chunkId,
        UUID documentId,
        int chunkIndex,
        String content,
        String sourceTitle,
        double score,
        double vectorScore,
        boolean lexicalHit) {

    public RetrievedChunk(
            UUID chunkId,
            UUID documentId,
            int chunkIndex,
            String content,
            String sourceTitle,
            double score) {
        this(chunkId, documentId, chunkIndex, content, sourceTitle, score, score, false);
    }

    public RetrievedChunk withScore(double newScore) {
        return new RetrievedChunk(
                chunkId, documentId, chunkIndex, content, sourceTitle, newScore, vectorScore, lexicalHit);
    }
}
