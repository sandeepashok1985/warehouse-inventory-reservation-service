package com.wirs.inventory.reservation.domain.model;

import java.util.Objects;
import lombok.Builder;

/** Immutable value object representing one line item in a reservation. */
@Builder
public record ReservationItem(String sku, long quantity) {

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
