package com.wirs.inventory.reservation.api.dto.response;

import java.util.List;
import lombok.Builder;
import org.springframework.data.domain.Page;

/** Pagination wrapper for list endpoints. */
@Builder
public record PagedResponse<T>(
    List<T> content,
    int page,
    int size,
    long totalElements,
    int totalPages
) {

    /** Constructs from a Spring Data Page. */
    public static <T> PagedResponse<T> from(Page<T> page) {
        return new PagedResponse<>(
            page.getContent(),
            page.getNumber(),
            page.getSize(),
            page.getTotalElements(),
            page.getTotalPages()
        );
    }
}
