package com.unilodge.common.dto;

import java.util.List;

/** Simple pagination envelope used by every list endpoint. */
public record PagedResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean last
) {
    public static <T> PagedResponse<T> of(List<T> content, int page, int size, long totalElements) {
        int totalPages = size <= 0 ? 0 : (int) Math.ceil((double) totalElements / size);
        return new PagedResponse<>(content, page, size, totalElements, totalPages, page + 1 >= totalPages);
    }

    public static <T> PagedResponse<T> empty(int page, int size) {
        return new PagedResponse<>(List.of(), page, size, 0, 0, true);
    }
}
