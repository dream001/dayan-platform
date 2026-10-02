package com.dayan.platform.vo;

import java.util.List;
import java.util.Objects;

public record PageResponse<T>(
        int page,
        int size,
        long total,
        long totalPages,
        List<T> items
) {

    public PageResponse {
        if (page < 1 || size < 1 || total < 0) {
            throw new IllegalArgumentException("Invalid page metadata");
        }
        items = List.copyOf(Objects.requireNonNull(items, "items must not be null"));
    }

    public static <T> PageResponse<T> of(int page, int size, long total, List<T> items) {
        long totalPages = total / size + (total % size == 0 ? 0 : 1);
        return new PageResponse<>(page, size, total, totalPages, items);
    }
}
