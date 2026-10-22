package com.memos.application.rag;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class LexicalOverlapRerankerTest {

    private final LexicalOverlapReranker reranker = new LexicalOverlapReranker();

    @Test
    void boostsExactIdentifierMatchesAheadOfWeakerSemanticHits() {
        UUID doc = UUID.randomUUID();
        RetrievedChunk semantic = new RetrievedChunk(
                UUID.randomUUID(), doc, 0, "general discussion of networking", "a.md", 0.05, 0.9, false);
        RetrievedChunk identifier = new RetrievedChunk(
                UUID.randomUUID(), doc, 1, "throw new ECONNRESET after timeout", "b.md", 0.04, 0.2, true);

        List<RetrievedChunk> reranked = reranker.rerank("ECONNRESET after timeout", List.of(semantic, identifier), 2);

        assertThat(reranked.get(0).content()).contains("ECONNRESET");
        assertThat(reranked.get(0).lexicalHit()).isTrue();
        assertThat(reranked).hasSize(2);
    }

    @Test
    void returnsAtMostTopK() {
        UUID doc = UUID.randomUUID();
        List<RetrievedChunk> candidates = List.of(
                new RetrievedChunk(UUID.randomUUID(), doc, 0, "one", "a.md", 0.3),
                new RetrievedChunk(UUID.randomUUID(), doc, 1, "two", "a.md", 0.2),
                new RetrievedChunk(UUID.randomUUID(), doc, 2, "three", "a.md", 0.1));

        assertThat(reranker.rerank("one", candidates, 1)).hasSize(1);
    }
}
