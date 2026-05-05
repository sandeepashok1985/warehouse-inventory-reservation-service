package com.wirs.inventory.reservation.domain.model;

import java.util.Objects;
import lombok.Builder;

/** Immutable value object representing one line item in a reservation. */
@Builder
public record ReservationItem(String sku, long quantity) {

    /**
     * Compact canonical constructor — validates invariants on every creation.
     *
     * @param sku      the SKU identifier; must not be null or blank
     * @param quantity the quantity to reserve; must be positive
     * @throws NullPointerException     if sku is null
     * @throws IllegalArgumentException if sku is blank or quantity is not positive
     */
    public ReservationItem {
        Objects.requireNonNull(sku, "SKU must not be null");
        if (sku.isBlank()) {
            throw new IllegalArgumentException("SKU must not be blank");
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be positive for SKU: " + sku);
        }
    }
}
