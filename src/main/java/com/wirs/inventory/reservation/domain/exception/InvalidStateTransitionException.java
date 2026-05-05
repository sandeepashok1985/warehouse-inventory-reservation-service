package com.wirs.inventory.reservation.domain.exception;

/** Thrown when a reservation state transition is not permitted from the current state. */
public class InvalidStateTransitionException extends RuntimeException {

    /**
     * @param message a human-readable description of the invalid transition
     */
    public InvalidStateTransitionException(String message) {
        super(message);
    }
}
