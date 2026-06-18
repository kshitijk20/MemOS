package com.memos.domain.document;

/**
 * Type of source a document was ingested from. New ingestion sources (Jira, Confluence,
 * Slack, GitHub issues, ...) can be added here together with a corresponding
 * {@code DocumentParserPort} implementation registered in
 * {@code infrastructure.ingestion.parser.DocumentParserRegistry} — no changes to the
 * application-layer ingestion service are required.
 */
public enum DocumentSourceType {
    PDF,
    MARKDOWN,
    GIT_REPOSITORY
}
