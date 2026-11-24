package com.habitloop.common.web;

import org.springframework.data.domain.Page;

import java.util.List;

/** Serializable pagination wrapper (see docs/05 §8). */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
    public static <T> PageResponse<T> of(Page<T> page) {
        return new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages());
    }
}
