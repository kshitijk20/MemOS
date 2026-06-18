package com.memos.web.common;

import java.util.List;
import java.util.function.Function;

import com.memos.domain.common.PageResult;

public record PageResponse<T>(List<T> items, long totalElements, int totalPages, int page, int size) {

    public static <T> PageResponse<T> of(PageResult<T> result) {
        return new PageResponse<>(result.items(), result.totalElements(), result.totalPages(), result.page(), result.size());
    }

    public static <S, T> PageResponse<T> of(PageResult<S> result, Function<S, T> mapper) {
        return of(result.map(mapper));
    }
}
