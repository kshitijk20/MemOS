package com.memos.application.rag;

import java.util.List;
import java.util.UUID;

/**
 * Retrieval over a user's document chunks: vector similarity and hybrid (vector + keyword).
 */
public interface RetrievalPort {

    /** Searches all COMPLETED documents owned by {@code ownerId}. */
    List<RetrievedChunk> findSimilar(UUID ownerId, float[] queryEmbedding, int topK);

    /** Searches only within the given document IDs (must be owned by {@code ownerId}). */
    List<RetrievedChunk> findSimilarInScope(UUID ownerId, float[] queryEmbedding, List<UUID> documentScope, int topK);

    /**
     * Hybrid retrieval: cosine similarity plus keyword/identifier search, fused with RRF.
     * {@code documentScope} empty or null searches all completed documents for the owner.
     */
    List<RetrievedChunk> findHybrid(
            UUID ownerId,
            String query,
            float[] queryEmbedding,
            List<UUID> documentScope,
            int limit);
}
