package com.memos.application.rag;

import java.util.List;
import java.util.UUID;

/**
 * Port for vector-similarity retrieval over a user's document chunks.
 */
public interface RetrievalPort {

    /**
     * Returns the {@code topK} chunks most similar to {@code queryEmbedding}, restricted to
     * documents owned by {@code ownerId}, ordered by similarity descending.
     */
    List<RetrievedChunk> findSimilar(UUID ownerId, float[] queryEmbedding, int topK);
}
