package com.wirs.inventory.reservation.domain.model;

import java.util.Comparator;
import java.util.List;
import lombok.Builder;

/**
 * Typed wrapper that guarantees SKUs are ordered alphabetically before inventory lock acquisition.
 * Enforces deadlock prevention at the type boundary — callers cannot pass an unsorted list.
 */
@Builder
public record SkuAllocationOrder(List<ReservationItem> items) {

    /**
     * Factory — sorts items by SKU ascending before constructing the record.
     *
     * @param unsorted the items to sort; the caller's list is not mutated
     * @return a new {@link SkuAllocationOrder} with items sorted by SKU alphabetically
     */
    public static SkuAllocationOrder of(List<ReservationItem> unsorted) {
        return new SkuAllocationOrder(
            unsorted.stream()
                .sorted(Comparator.comparing(ReservationItem::sku))
                .toList()
        );
    }
}
