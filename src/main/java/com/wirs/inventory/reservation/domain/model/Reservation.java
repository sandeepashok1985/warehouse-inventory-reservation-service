package com.wirs.inventory.reservation.domain.model;

import com.wirs.inventory.reservation.domain.state.ReservationState;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;
import lombok.Singular;

/** Aggregate root representing a warehouse inventory reservation. */
@Getter
@Builder
public class Reservation {

    private final UUID id;
    private final String orderId;
    private ReservationState state;
    @Singular
    private final List<ReservationItem> items;
    private final Instant createdAt;
    private Instant updatedAt;
    private final Instant expiresAt;

    /** Transitions this reservation to CONFIRMED state via the state machine. */
    public void confirm() {
        this.state = state.confirm();
    }

    /** Transitions this reservation to CANCELLED state via the state machine. */
    public void cancel() {
        this.state = state.cancel();
    }

    /** Returns true if the reservation TTL has elapsed relative to the given instant. */
    public boolean isExpired(Instant now) {
        return now.isAfter(expiresAt);
    }

    /** Returns the string name of the current state (e.g., "PENDING"). */
    public String status() {
        return state.name();
    }

    /** Sets the last-updated timestamp. */
    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
