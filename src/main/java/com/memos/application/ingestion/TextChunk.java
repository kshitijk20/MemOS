package com.memos.application.ingestion;

import java.util.Map;

/**
 * A chunk of text produced by a {@link ChunkingPort}, ready to be embedded and persisted as a
 * {@code DocumentChunk}.
 */
public record TextChunk(String content, int tokenCount, Map<String, Object> metadata) {
}
