package com.memos.application.rag;

import java.util.List;

/**
 * Reorders hybrid-retrieval candidates before they are placed in the prompt.
 */
public interface RerankPort {

    List<RetrievedChunk> rerank(String query, List<RetrievedChunk> candidates, int topK);
}
