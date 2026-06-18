package com.memos.infrastructure.persistence.repository;

import java.util.UUID;

/**
 * Projection for the PGVector similarity search used by the RAG retrieval pipeline.
 * {@code score} is cosine similarity (1 - cosine distance), higher is more relevant.
 */
public interface SimilarChunkProjection {

    UUID getId();

    UUID getDocumentId();

    int getChunkIndex();

    String getContent();

    String getSourceTitle();

    double getScore();
}
