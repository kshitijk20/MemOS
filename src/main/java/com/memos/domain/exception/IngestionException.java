package com.memos.domain.exception;

/**
 * Raised when a document fails to be parsed, chunked, embedded, or persisted during ingestion.
 */
public class IngestionException extends DomainException {

    public IngestionException(String message) {
        super(message);
    }

    public IngestionException(String message, Throwable cause) {
        super(message, cause);
    }
}
