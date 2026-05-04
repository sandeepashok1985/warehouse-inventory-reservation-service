package com.wirs.inventory.reservation.api.dto.response;

import lombok.Builder;

/** Response DTO for a single reservation line item. */
@Builder
public record ReservationItemResponse(String sku, long quantity) {}
