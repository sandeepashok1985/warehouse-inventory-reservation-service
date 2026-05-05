package com.wirs.inventory.reservation.domain.event;

/**
 * Event type keys for reservation lifecycle domain events.
 *
 * Each constant maps to a specific state transition in the reservation
 * state machine, used both in the event records and as the NATS subject
 * discriminator (e.g., {@code reservations.created}).
 */
public enum ReservationEventType {
    /** Emitted when a new reservation is created in PENDING state. */
    RESERVATION_CREATED,
    /** Emitted when a PENDING reservation is transitioned to CONFIRMED. */
    RESERVATION_CONFIRMED,
    /** Emitted when a reservation is cancelled (API or TTL expiry). */
    RESERVATION_CANCELLED
}
