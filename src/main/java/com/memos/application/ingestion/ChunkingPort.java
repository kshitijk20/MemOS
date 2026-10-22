package com.memos.application.ingestion;

import java.util.List;

/**
 * Splits extracted document text into semantically coherent chunks suitable for embedding.
 */
public interface ChunkingPort {

    /**
     * Page separator a parser may put on its own paragraph between pages (e.g. PDF). The chunker
     * removes it and records each chunk's page range as {@code pageStart}/{@code pageEnd} metadata.
     * A private-use character, so it never collides with real document text.
     */
    String PAGE_BREAK = "\uE000";

    List<TextChunk> chunk(String content);
}
