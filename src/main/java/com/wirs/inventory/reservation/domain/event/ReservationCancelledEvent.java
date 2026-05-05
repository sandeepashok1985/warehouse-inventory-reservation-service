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

    /**
     * Creates a {@link ReservationCancelledEvent} from a domain reservation.
     *
     * @param reservation the source reservation (must not be null)
     * @param reason      the cancellation reason (e.g., "API" or "TTL_EXPIRED")
     * @param occurredAt  the timestamp when the event occurred
     * @return a new event instance with data copied from the reservation
     */
    public static ReservationCancelledEvent from(Reservation reservation, String reason, Instant occurredAt) {
        return ReservationCancelledEvent.builder()
            .reservationId(reservation.getId())
            .orderId(reservation.getOrderId())
            .reason(reason)
            .occurredAt(occurredAt)
            .build();
    }
}
