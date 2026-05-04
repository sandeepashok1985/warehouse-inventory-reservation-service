package com.wirs.inventory.reservation.domain.state;

/** Contract for all reservation states. Each state defines its own valid transitions. */
public abstract class ReservationState {

    /**
     * Transitions to CONFIRMED state.
     *
     * @throws com.wirs.inventory.reservation.domain.exception.InvalidStateTransitionException
     *     if the transition is not valid from this state.
     */
    public abstract ReservationState confirm();

    /**
     * Transitions to CANCELLED state.
     *
     * @throws com.wirs.inventory.reservation.domain.exception.InvalidStateTransitionException
     *     if the transition is not valid from this state.
     */
    public abstract ReservationState cancel();

    /** Returns the string name of this state, matching the database CHECK constraint values. */
    public abstract String name();

    /**
     * Reconstructs a state instance from its persisted string name.
     *
     * @throws IllegalArgumentException if the status string does not match a known state.
     */
    public static ReservationState fromString(String status) {
        return switch (status) {
            case "PENDING"   -> new PendingState();
            case "CONFIRMED" -> new ConfirmedState();
            case "CANCELLED" -> new CancelledState();
            default -> throw new IllegalArgumentException("Unknown reservation status: " + status);
        };
    }
}
