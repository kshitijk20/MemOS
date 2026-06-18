package com.memos.domain.exception;

public class ResourceNotFoundException extends DomainException {

    public ResourceNotFoundException(String resourceName, Object identifier) {
        super(resourceName + " not found: " + identifier);
    }
}
