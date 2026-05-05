package com.wirs.inventory.reservation.domain.exception;

/**
 * Thrown when a SKU exists in the product catalog but has no inventory record.
 * This is a data integrity failure, not a client error — maps to HTTP 500.
 */
public class InventoryNotInitializedException extends RuntimeException {

    /**
     * @param sku the SKU whose inventory record is missing
     */
    public InventoryNotInitializedException(String sku) {
        super("Inventory record missing for SKU: " + sku
            + " — product exists but was never initialized in inventory");
    }
}
