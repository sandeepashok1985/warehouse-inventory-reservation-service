package com.wirs.inventory.reservation.domain.exception;

/** Thrown when a client requests a SKU that does not exist in the product catalog. */
public class SkuNotFoundException extends RuntimeException {

    /**
     * @param sku the SKU that was not found, included in the message
     */
    public SkuNotFoundException(String sku) {
        super("SKU not found: " + sku);
    }
}
