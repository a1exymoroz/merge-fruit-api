package com.mergefruit.backend.dto;

import java.util.List;
import org.springframework.data.domain.Page;

/*
 Learning Notes

 What: Generic paginated response wrapper.
 Why: Pagination limits rows per request — protects DB from unbounded SELECT * queries.
*/
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean hasNext
) {
    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.hasNext()
        );
    }
}
