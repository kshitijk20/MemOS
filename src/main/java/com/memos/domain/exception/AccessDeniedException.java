package com.memos.domain.exception;

/**
 * Raised when an authenticated user attempts to access a resource they do not own.
 */
public class AccessDeniedException extends DomainException {

    public AccessDeniedException(String message) {
        super(message);
    }
}
