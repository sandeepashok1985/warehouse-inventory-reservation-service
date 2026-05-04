package com.wirs.inventory.reservation.domain.exception;

/** Thrown when a client requests a SKU that does not exist in the product catalog. */
public class SkuNotFoundException extends RuntimeException {

    public SkuNotFoundException(String sku) {
        super("SKU not found: " + sku);
    }
}
