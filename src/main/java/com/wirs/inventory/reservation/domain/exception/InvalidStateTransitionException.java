package com.wirs.inventory.reservation.domain.exception;

/** Thrown when a reservation state transition is not permitted from the current state. */
public class InvalidStateTransitionException extends RuntimeException {

    public InvalidStateTransitionException(String message) {
        super(message);
    }
}
