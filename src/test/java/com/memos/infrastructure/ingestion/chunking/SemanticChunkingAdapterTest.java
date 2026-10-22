package com.memos.infrastructure.ingestion.chunking;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.memos.application.ingestion.TextChunk;

class SemanticChunkingAdapterTest {

    @Test
    void blankContentProducesNoChunks() {
        SemanticChunkingAdapter adapter = new SemanticChunkingAdapter(500, 50);

        assertThat(adapter.chunk("")).isEmpty();
        assertThat(adapter.chunk("   \n\n  ")).isEmpty();
    }

    @Test
    void shortContentProducesSingleChunk() {
        SemanticChunkingAdapter adapter = new SemanticChunkingAdapter(500, 50);

        List<TextChunk> chunks = adapter.chunk("This is a short paragraph.\n\nAnd a second one.");

        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0).content()).contains("This is a short paragraph.").contains("And a second one.");
    }

    @Test
    void longContentIsSplitAcrossMultipleChunksWithOverlap() {
        // chunkSizeTokens=10 -> ~40 chars per chunk; each paragraph below is ~30 chars (~8 tokens)
        SemanticChunkingAdapter adapter = new SemanticChunkingAdapter(10, 2);

        String paragraph1 = "Alpha bravo charlie delta echo.";
        String paragraph2 = "Foxtrot golf hotel india juliet.";
        String paragraph3 = "Kilo lima mike november oscar.";
        String content = String.join("\n\n", paragraph1, paragraph2, paragraph3);

        List<TextChunk> chunks = adapter.chunk(content);

        assertThat(chunks).hasSizeGreaterThan(1);
        // every chunk after the first should carry some trailing overlap from the previous one
        for (TextChunk chunk : chunks) {
            assertThat(chunk.tokenCount()).isGreaterThan(0);
            assertThat(chunk.metadata()).containsKey("chunkIndexInSource");
        }
    }

    @Test
    void oversizedParagraphIsHardSplit() {
        SemanticChunkingAdapter adapter = new SemanticChunkingAdapter(5, 0);

        // a single "word" with no spaces or sentence boundaries, much longer than 5 tokens (20 chars)
        String hugeWord = "a".repeat(100);

        List<TextChunk> chunks = adapter.chunk(hugeWord);

        assertThat(chunks).hasSizeGreaterThan(1);
        for (TextChunk chunk : chunks) {
            assertThat(chunk.tokenCount()).isLessThanOrEqualTo(5);
        }
    }

    @Test
    void recordsPageRangesWhenContentHasPageBreaks() {
        SemanticChunkingAdapter adapter = new SemanticChunkingAdapter(20, 0);
        String br = "\n\n" + com.memos.application.ingestion.ChunkingPort.PAGE_BREAK + "\n\n";
        String content = "Page one intro text." + br
                + "Page two has a fairly long paragraph that fills a chunk on its own." + br
                + "Page three.";

        var chunks = adapter.chunk(content);

        assertThat(chunks).isNotEmpty();
        assertThat(chunks).allSatisfy(chunk -> assertThat(chunk.content())
                .doesNotContain(com.memos.application.ingestion.ChunkingPort.PAGE_BREAK));
        assertThat(chunks.get(0).metadata()).containsEntry("pageStart", 1);
        var last = chunks.get(chunks.size() - 1);
        assertThat(last.metadata()).containsEntry("pageEnd", 3);
    }

    @Test
    void omitsPageMetadataForUnpagedContent() {
        SemanticChunkingAdapter adapter = new SemanticChunkingAdapter(500, 50);

        var chunks = adapter.chunk("Just a markdown paragraph.");

        assertThat(chunks.get(0).metadata()).doesNotContainKeys("pageStart", "pageEnd");
    }
}
