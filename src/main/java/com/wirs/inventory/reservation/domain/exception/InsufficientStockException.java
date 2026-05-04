package com.wirs.inventory.reservation.domain.exception;

/** Thrown when a SKU does not have enough available stock to satisfy a reservation request. */
public class InsufficientStockException extends RuntimeException {

    /** Constructs with human-readable message including sku, requested, and available quantities. */
    public InsufficientStockException(String sku, long requested, long available) {
        super("SKU " + sku + " has only " + available + " units available; "
            + requested + " were requested");
    }
}
