package com.wirs.inventory.reservation.domain.state;

/** Represents a reservation that has been created but not yet confirmed or cancelled. */
public class PendingState extends ReservationState {

    private static final String NAME = "PENDING";

    @Override
    public ReservationState confirm() {
        return new ConfirmedState();
    }

    @Override
    public ReservationState cancel() {
        return new CancelledState();
    }

    @Override
    public String name() {
        return NAME;
    }

    public static String getName() {
        return NAME;
    }
}
