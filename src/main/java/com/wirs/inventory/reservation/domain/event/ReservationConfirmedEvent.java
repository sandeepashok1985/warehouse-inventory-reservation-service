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

    /**
     * Creates a {@link ReservationConfirmedEvent} from a domain reservation.
     *
     * @param reservation the source reservation (must not be null)
     * @param occurredAt  the timestamp when the event occurred
     * @return a new event instance with data copied from the reservation
     */
    public static ReservationConfirmedEvent fromReservation(Reservation reservation, Instant occurredAt) {
        return ReservationConfirmedEvent.builder()
            .reservationId(reservation.getId())
            .orderId(reservation.getOrderId())
            .occurredAt(occurredAt)
            .build();
    }
}
