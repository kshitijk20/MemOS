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
    GIT_REPOSITORY,
    WEB_PAGE,
    /** A photo or screenshot (notes, whiteboard, receipt); its text is read with OCR. */
    IMAGE;

    /** Uploaded by the user (kept on disk until ingested), as opposed to fetched from a URL. */
    public boolean isUploadedFile() {
        return this == PDF || this == MARKDOWN || this == IMAGE;
    }
}
