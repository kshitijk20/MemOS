package com.memos.application.rag;

import java.util.UUID;

/**
 * A document chunk returned by {@link RetrievalPort} as relevant context for a question,
 * along with its similarity {@code score} (cosine similarity, higher is more relevant).
 */
public record RetrievedChunk(
        UUID chunkId,
        UUID documentId,
        int chunkIndex,
        String content,
        String sourceTitle,
        double score) {
}
