package com.wirs.inventory.reservation.domain.exception;

/**
 * Thrown when a SKU exists in the product catalog but has no inventory record.
 * This is a data integrity failure, not a client error — maps to HTTP 500.
 */
public class InventoryNotInitializedException extends RuntimeException {

    public InventoryNotInitializedException(String sku) {
        super("Inventory record missing for SKU: " + sku
            + " — product exists but was never initialized in inventory");
    }
}
