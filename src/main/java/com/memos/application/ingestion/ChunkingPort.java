package com.memos.application.ingestion;

import java.util.List;

/**
 * Splits extracted document text into semantically coherent chunks suitable for embedding.
 */
public interface ChunkingPort {

    List<TextChunk> chunk(String content);
}
