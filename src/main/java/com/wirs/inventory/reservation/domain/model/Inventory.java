package com.wirs.inventory.reservation.domain.model;

import lombok.Builder;

/** Aggregate representing the stock levels for a single SKU. */
@Builder
public record Inventory(
    String sku,
    long totalStock,
    long availableStock,
    long reservedStock,
    long version
) {

    /**
     * Compact canonical constructor — validates the stock balance invariant
     * on every creation.
     */
    public Inventory {
        if (totalStock != availableStock + reservedStock) {
            throw new AssertionError(
                "Stock balance invariant violated for SKU " + sku
                    + ": total=" + totalStock
                    + ", available=" + availableStock
                    + ", reserved=" + reservedStock);
        }
    }
}
