package com.wirs.inventory.reservation.domain.event;

import com.wirs.inventory.reservation.application.event.DomainEvent;
import com.wirs.inventory.reservation.domain.model.Reservation;

import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

/** Emitted when a reservation is cancelled — either by API ("API") or TTL expiry ("TTL_EXPIRED"). */
@Builder
public record ReservationCancelledEvent(
    UUID reservationId,
    String orderId,
    String reason,
    Instant occurredAt
) implements DomainEvent {

    @Override
    public UUID aggregateId() {
        return reservationId;
    }

    @Override
    public ReservationEventType eventType() {
        return ReservationEventType.RESERVATION_CANCELLED;
    }

    public static ReservationCancelledEvent from(Reservation reservation, String reason, Instant occurredAt) {
        return ReservationCancelledEvent.builder()
            .reservationId(reservation.getId())
            .orderId(reservation.getOrderId())
            .reason(reason)
            .occurredAt(occurredAt)
            .build();
    }
}
