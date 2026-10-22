package com.memos.application.rag;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class ReciprocalRankFusionTest {

    @Test
    void prefersChunksThatAppearInBothLists() {
        UUID both = UUID.randomUUID();
        UUID vectorOnly = UUID.randomUUID();
        UUID keywordOnly = UUID.randomUUID();
        UUID doc = UUID.randomUUID();

        RetrievedChunk sharedVector = new RetrievedChunk(both, doc, 0, "shared", "a.md", 0.9, 0.9, false);
        RetrievedChunk onlyVector = new RetrievedChunk(vectorOnly, doc, 1, "vector", "a.md", 0.8, 0.8, false);
        RetrievedChunk sharedKeyword = new RetrievedChunk(both, doc, 0, "shared", "a.md", 0.4, 0, true);
        RetrievedChunk onlyKeyword = new RetrievedChunk(keywordOnly, doc, 2, "keyword", "a.md", 0.7, 0, true);

        List<RetrievedChunk> fused = ReciprocalRankFusion.fuse(
                List.of(sharedVector, onlyVector),
                List.of(sharedKeyword, onlyKeyword),
                60);

        assertThat(fused).hasSize(3);
        assertThat(fused.get(0).chunkId()).isEqualTo(both);
        assertThat(fused.get(0).lexicalHit()).isTrue();
        assertThat(fused.get(0).vectorScore()).isEqualTo(0.9);
        assertThat(fused).extracting(RetrievedChunk::chunkId).contains(vectorOnly, keywordOnly);
    }
}
