package com.memos.application.ingestion;

import java.util.Map;

/**
 * A single parsed unit of text extracted from a {@code Document}'s source. PDFs and Markdown
 * files produce a single {@code ParsedDocument}; a Git repository produces one per matched
 * file, each carrying its repo-relative path in {@code metadata}.
 *
 * @param sourceLabel human-readable label for this unit (e.g. file path), used for citations
 * @param content     extracted plain text to be chunked and embedded
 * @param metadata    additional context (e.g. {@code filePath}) merged into each resulting chunk
 */
public record ParsedDocument(String sourceLabel, String content, Map<String, Object> metadata) {
}
