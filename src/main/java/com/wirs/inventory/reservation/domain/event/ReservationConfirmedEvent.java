package com.wirs.inventory.reservation.domain.event;

import com.wirs.inventory.reservation.application.event.DomainEvent;
import com.wirs.inventory.reservation.domain.model.Reservation;

import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

/** Emitted when a PENDING reservation is transitioned to CONFIRMED. */
@Builder
public record ReservationConfirmedEvent(
    UUID reservationId,
    String orderId,
    Instant occurredAt
) implements DomainEvent {

    @Override
    public UUID aggregateId() {
        return reservationId;
    }

    @Override
    public ReservationEventType eventType() {
        return ReservationEventType.RESERVATION_CONFIRMED;
    }

    public static ReservationConfirmedEvent fromReservation(Reservation reservation, Instant occurredAt) {
        return ReservationConfirmedEvent.builder()
            .reservationId(reservation.getId())
            .orderId(reservation.getOrderId())
            .occurredAt(occurredAt)
            .build();
    }
}
