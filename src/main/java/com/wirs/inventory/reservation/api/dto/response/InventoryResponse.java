package com.wirs.inventory.reservation.api.dto.response;

import com.wirs.inventory.reservation.domain.model.Inventory;
import lombok.Builder;

/** Response DTO representing the current stock state for a single SKU. */
@Builder
public record InventoryResponse(
    String sku,
    long totalStock,
    long availableStock,
    long reservedStock
) {

    /**
     * Creates an {@link InventoryResponse} from a domain {@link Inventory} aggregate.
     *
     * @param inventory the domain inventory aggregate (never {@code null})
     * @return a new response DTO with matching values
     */
    public static InventoryResponse from(Inventory inventory) {
        return InventoryResponse.builder()
            .sku(inventory.sku())
            .totalStock(inventory.totalStock())
            .availableStock(inventory.availableStock())
            .reservedStock(inventory.reservedStock())
            .build();
    }
}
