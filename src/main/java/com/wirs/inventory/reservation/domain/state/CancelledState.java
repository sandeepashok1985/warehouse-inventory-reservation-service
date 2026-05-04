package com.wirs.inventory.reservation.domain.state;

import com.wirs.inventory.reservation.domain.exception.InvalidStateTransitionException;

/** Represents a reservation that has been cancelled and whose stock has been released. */
public class CancelledState extends ReservationState {

    private static final String NAME = "CANCELLED";

    @Override
    public ReservationState confirm() {
        throw new InvalidStateTransitionException(
            "Cannot confirm a CANCELLED reservation");
    }

    @Override
    public ReservationState cancel() {
        throw new InvalidStateTransitionException(
            "Cannot cancel a reservation that is already CANCELLED");
    }

    @Override
    public String name() {
        return NAME;
    }

    public static String getName() {
        return NAME;
    }
}
