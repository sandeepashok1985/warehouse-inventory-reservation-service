package com.wirs.inventory.reservation.api.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;

/** Line item in a reservation request: one SKU and quantity. */
@Builder
public record ReserveItemRequest(
    @NotBlank(message = "SKU must not be blank") String sku,
    @NotNull @Min(value = 1, message = "Quantity must be at least 1") Long quantity
) {}
