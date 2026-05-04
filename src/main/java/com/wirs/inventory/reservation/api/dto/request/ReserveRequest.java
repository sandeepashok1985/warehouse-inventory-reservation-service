package com.wirs.inventory.reservation.api.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import lombok.Builder;

/** Request body for POST /api/v1/reservations. */
@Builder
public record ReserveRequest(
    @NotBlank(message = "Order ID must not be blank") String orderId,
    @NotEmpty(message = "Items list must not be empty") @Valid List<ReserveItemRequest> items
) {}
