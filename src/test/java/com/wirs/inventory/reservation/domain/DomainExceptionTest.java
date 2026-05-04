package com.wirs.inventory.reservation.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.wirs.inventory.reservation.domain.exception.DuplicateOrderException;
import com.wirs.inventory.reservation.domain.exception.InventoryNotInitializedException;
import com.wirs.inventory.reservation.domain.exception.InsufficientStockException;
import com.wirs.inventory.reservation.domain.exception.ReservationNotFoundException;
import com.wirs.inventory.reservation.domain.model.Reservation;
import com.wirs.inventory.reservation.domain.model.ReservationItem;
import com.wirs.inventory.reservation.domain.state.PendingState;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
class DomainExceptionTest {

    @Test
    void insufficientStockException_messageContainsSkuAndQuantities() {
        var ex = new InsufficientStockException("A100", 50, 30);
        assertThat(ex.getMessage()).contains("A100", "50", "30");
    }

    @Test
    void inventoryNotInitializedException_messageContainsSku() {
        var ex = new InventoryNotInitializedException("X999");
        assertThat(ex.getMessage()).contains("X999");
    }

    @Test
    void reservationNotFoundException_messageContainsId() {
        var id = UUID.randomUUID();
        var ex = new ReservationNotFoundException(id);
        assertThat(ex.getMessage()).contains(id.toString());
    }

    @Test
    void duplicateOrderException_exposesExistingReservation() {
        var reservation = Reservation.builder()
            .id(UUID.randomUUID())
            .orderId("ORD-1")
            .state(new PendingState())
            .items(List.of(new ReservationItem("A100", 1)))
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(600))
            .build();
        var ex = new DuplicateOrderException(reservation);
        assertThat(ex.getExistingReservation()).isSameAs(reservation);
    }
}
