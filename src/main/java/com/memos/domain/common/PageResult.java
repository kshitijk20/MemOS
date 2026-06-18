package com.memos.domain.common;

import java.util.List;
import java.util.function.Function;

/**
 * Framework-agnostic pagination result used by domain ports.
 */
public record PageResult<T>(List<T> items, long totalElements, int page, int size) {

    public <R> PageResult<R> map(Function<T, R> mapper) {
        return new PageResult<>(items.stream().map(mapper).toList(), totalElements, page, size);
    }

    public int totalPages() {
        return size == 0 ? 0 : (int) Math.ceil((double) totalElements / size);
    }
}
