package com.wirs.inventory.reservation.domain.model;

import java.util.List;
import java.util.UUID;
import lombok.Builder;

/**
 * Typed wrapper that guarantees reservation IDs are UUID-sorted before any batch lock acquisition.
 * Required by any repository method that touches more than one reservation row via SELECT FOR UPDATE.
 */
@Builder
public record OrderedIdList(List<UUID> ids) {

    /** Factory — sorts IDs by natural UUID order (lexicographic) before constructing the record. */
    public static OrderedIdList of(List<UUID> unsorted) {
        return new OrderedIdList(
            unsorted.stream()
                .sorted()
                .toList()
        );
    }
}
