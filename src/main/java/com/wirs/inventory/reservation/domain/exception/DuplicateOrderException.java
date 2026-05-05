package com.wirs.inventory.reservation.domain.exception;

import com.wirs.inventory.reservation.domain.model.Reservation;

/** Thrown when an attempt is made to create a reservation for an order ID that already exists. */
public class DuplicateOrderException extends RuntimeException {

    private final Reservation existingReservation;

    /**
     * @param existing the previously created reservation for the same orderId
     */
    public DuplicateOrderException(Reservation existing) {
        super("Order already exists: " + existing.getOrderId());
        this.existingReservation = existing;
    }

    public Reservation getExistingReservation() {
        return existingReservation;
    }
}
