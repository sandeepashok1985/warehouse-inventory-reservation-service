package com.wirs.inventory.reservation.domain.exception;

/** Thrown when a SKU does not have enough available stock to satisfy a reservation request. */
public class InsufficientStockException extends RuntimeException {

    /**
     * @param sku       the SKU that lacks sufficient stock
     * @param requested the quantity that was requested
     * @param available the quantity that is actually available
     */
    public InsufficientStockException(String sku, long requested, long available) {
        super("SKU " + sku + " has only " + available + " units available; "
            + requested + " were requested");
    }
}
