package com.wirs.inventory.reservation.api.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.Builder;

/** Response DTO for a single reservation. */
@Builder
public record ReservationResponse(
    UUID id,
    String orderId,
    String status,
    List<ReservationItemResponse> items,
    Instant createdAt,
    Instant updatedAt,
    Instant expiresAt
) {}
