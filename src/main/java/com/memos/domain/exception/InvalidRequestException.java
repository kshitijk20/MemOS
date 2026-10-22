package com.memos.domain.exception;

/** Raised when a request is well-formed but not valid for the current state (maps to HTTP 400). */
public class InvalidRequestException extends DomainException {

    public InvalidRequestException(String message) {
        super(message);
    }
}
