package com.wirs.inventory.reservation.domain.exception;

import java.util.UUID;

/** Thrown when a reservation cannot be found by its identifier. */
public class ReservationNotFoundException extends RuntimeException {

    public ReservationNotFoundException(UUID reservationId) {
        super("Reservation not found: " + reservationId);
    }
}
