package com.wirs.inventory.reservation.domain.event;

import com.wirs.inventory.reservation.application.event.DomainEvent;
import com.wirs.inventory.reservation.domain.model.Reservation;
import com.wirs.inventory.reservation.domain.model.ReservationItem;

import lombok.Builder;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Emitted when a new reservation is created in PENDING state. */
@Builder
public record ReservationCreatedEvent(
    UUID reservationId,
    String orderId,
    List<ReservationItem> items,
    Instant expiresAt,
    Instant occurredAt
) implements DomainEvent {

    @Override
    public UUID aggregateId() {
        return reservationId;
    }

    @Override
    public ReservationEventType eventType() {
        return ReservationEventType.RESERVATION_CREATED;
    }

    public static ReservationCreatedEvent fromReservation(Reservation reservation, Instant occurredAt) {
        return ReservationCreatedEvent.builder()
            .reservationId(reservation.getId())
            .orderId(reservation.getOrderId())
            .items(reservation.getItems())
            .expiresAt(reservation.getExpiresAt())
            .occurredAt(occurredAt)
            .build();
    }
}
