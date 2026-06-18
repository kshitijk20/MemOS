package com.memos.domain.exception;

/**
 * Raised when an AI provider (chat or embedding) call fails.
 */
public class AiProviderException extends DomainException {

    public AiProviderException(String message, Throwable cause) {
        super(message, cause);
    }

    public AiProviderException(String message) {
        super(message);
    }
}
