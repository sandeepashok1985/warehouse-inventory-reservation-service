package com.wirs.inventory.reservation.domain.state;

import com.wirs.inventory.reservation.domain.exception.InvalidStateTransitionException;

/** Represents a reservation that has been confirmed and committed to fulfillment. */
public class ConfirmedState extends ReservationState {

    private static final String NAME = "CONFIRMED";

    @Override
    public ReservationState confirm() {
        throw new InvalidStateTransitionException(
            "Cannot confirm a reservation that is already CONFIRMED");
    }

    @Override
    public ReservationState cancel() {
        throw new InvalidStateTransitionException(
            "Cannot cancel a CONFIRMED reservation — stock is committed to fulfillment");
    }

    @Override
    public String name() {
        return NAME ;
    }

    public static String getName() {
        return NAME;
    }
}
