package com.memos.domain.common;

/**
 * Framework-agnostic pagination request used by domain ports, so the domain layer does not
 * depend on Spring Data types. Persistence adapters translate to/from Spring's Pageable.
 */
public record PageRequest(int page, int size) {

    public PageRequest {
        if (page < 0) {
            throw new IllegalArgumentException("page must be >= 0");
        }
        if (size <= 0 || size > 200) {
            throw new IllegalArgumentException("size must be between 1 and 200");
        }
    }

    public static PageRequest of(int page, int size) {
        return new PageRequest(page, size);
    }
}
