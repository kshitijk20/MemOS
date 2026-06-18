package com.memos.domain.ai;

import java.util.List;

/**
 * Port for the embedding model used by ingestion (chunk embeddings) and RAG retrieval
 * (query embeddings). A single embedding provider is configured globally
 * ({@code memos.ai.embedding.provider}) so that all stored vectors share the same
 * dimensionality and remain comparable.
 */
public interface EmbeddingPort {

    float[] embed(String text);

    List<float[]> embedBatch(List<String> texts);

    int dimensions();
}
