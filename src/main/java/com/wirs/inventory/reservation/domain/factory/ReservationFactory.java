package com.wirs.inventory.reservation.domain.factory;

import com.wirs.inventory.reservation.domain.model.Reservation;
import com.wirs.inventory.reservation.domain.model.ReservationItem;
import com.wirs.inventory.reservation.domain.state.PendingState;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

/**
 * Factory for creating new {@link Reservation} aggregates with the correct initial state,
 * ID generation, and expiry time.
 *
 * <p>Encapsulates the reservation construction logic that requires a {@link Clock} and
 * expiry configuration — the domain model itself stays stateless and testable with fixed time.</p>
 */
public class ReservationFactory {

    private final Clock clock;
    private final int expiryMinutes;

    public ReservationFactory(Clock clock, int expiryMinutes) {
        this.clock = clock;
        this.expiryMinutes = expiryMinutes;
    }

    /**
     * Creates a new PENDING reservation for the given order and items.
     *
     * @param orderId the external order reference; must not be null or blank.
     * @param items   the items to reserve; must not be null or empty.
     * @return a new Reservation in PENDING state with a random UUID and computed expiry.
     * @throws IllegalArgumentException if orderId is null/blank or items is null/empty.
     */
    public Reservation createPendingReservation(String orderId, List<ReservationItem> items) {
        if (orderId == null || orderId.isBlank()) {
            throw new IllegalArgumentException("Order ID must not be null or blank");
        }
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("Items must not be null or empty");
        }

        Instant now = Instant.now(clock);
        Instant expiresAt = now.plus(expiryMinutes, ChronoUnit.MINUTES);

        return Reservation.builder()
            .id(UUID.randomUUID())
            .orderId(orderId)
            .state(new PendingState())
            .items(items)
            .createdAt(now)
            .updatedAt(now)
            .expiresAt(expiresAt)
            .build();
    }
}
