package com.memos.infrastructure.persistence;

import java.util.function.Function;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.memos.domain.common.PageResult;

/**
 * Converts between the framework-agnostic {@code domain.common} pagination types and
 * Spring Data's {@code Pageable}/{@code Page}.
 */
public final class PageMappingUtil {

    private PageMappingUtil() {
    }

    public static Pageable toPageable(com.memos.domain.common.PageRequest pageRequest) {
        return org.springframework.data.domain.PageRequest.of(pageRequest.page(), pageRequest.size());
    }

    public static <T, R> PageResult<R> toPageResult(Page<T> page, Function<T, R> mapper) {
        return new PageResult<>(
                page.getContent().stream().map(mapper).toList(),
                page.getTotalElements(),
                page.getNumber(),
                page.getSize());
    }
}
