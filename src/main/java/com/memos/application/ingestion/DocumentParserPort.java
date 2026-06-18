package com.memos.application.ingestion;

import java.util.List;

import com.memos.domain.document.Document;
import com.memos.domain.document.DocumentSourceType;
import com.memos.domain.exception.IngestionException;

/**
 * Extracts text from a document's source. Implementations are registered in
 * {@code infrastructure.ingestion.parser.DocumentParserRegistry} and resolved by
 * {@link DocumentSourceType}. Adding a new ingestion source (Jira, Confluence, Slack, ...)
 * means adding a new {@link DocumentSourceType} and a corresponding parser - no changes to
 * {@link DocumentIngestionService}.
 */
public interface DocumentParserPort {

    DocumentSourceType sourceType();

    /**
     * @throws IngestionException if the source cannot be read or parsed
     */
    List<ParsedDocument> parse(Document document);
}
