package com.memos.domain.exception;

/**
 * Base type for exceptions raised by the domain/application layers. Kept independent of any
 * web or persistence framework so it can be mapped to the appropriate transport-level
 * representation (HTTP status, etc.) by the web layer.
 */
public abstract class DomainException extends RuntimeException {

    protected DomainException(String message) {
        super(message);
    }

    protected DomainException(String message, Throwable cause) {
        super(message, cause);
    }
}
