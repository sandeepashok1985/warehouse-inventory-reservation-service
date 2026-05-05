package com.wirs.inventory.reservation.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wirs.inventory.reservation.domain.state.CancelledState;
import com.wirs.inventory.reservation.domain.state.ConfirmedState;
import com.wirs.inventory.reservation.domain.state.PendingState;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
class ReservationModelTest {

    private static final Instant NOW = Instant.parse("2026-05-04T10:00:00Z");
    private static final Instant EXPIRES_AT = NOW.plus(10, ChronoUnit.MINUTES);

    private Reservation reservation;

    @BeforeEach
    void setUp() {
        reservation = Reservation.builder()
            .id(UUID.randomUUID())
            .orderId("ORD-MODEL")
            .state(new PendingState())
            .item(new ReservationItem("SKU-001", 5))
            .createdAt(NOW)
            .updatedAt(NOW)
            .expiresAt(EXPIRES_AT)
            .build();
    }

    @Test
    void isExpired_whenNowAfterExpiry_returnsTrue() {
        Instant afterExpiry = EXPIRES_AT.plus(1, ChronoUnit.SECONDS);
        assertThat(reservation.isExpired(afterExpiry)).isTrue();
    }

    @Test
    void isExpired_whenNowBeforeExpiry_returnsFalse() {
        Instant beforeExpiry = EXPIRES_AT.minus(1, ChronoUnit.SECONDS);
        assertThat(reservation.isExpired(beforeExpiry)).isFalse();
    }

    @Test
    void isExpired_whenNowEqualsExpiry_returnsFalse() {
        assertThat(reservation.isExpired(EXPIRES_AT)).isFalse();
    }

    @Test
    void status_returnsStateName() {
        assertThat(reservation.status()).isEqualTo("PENDING");
    }

    @Test
    void status_afterConfirm_returnsConfirmed() {
        reservation.confirm();
        assertThat(reservation.status()).isEqualTo("CONFIRMED");
    }

    @Test
    void status_afterCancel_returnsCancelled() {
        reservation.cancel();
        assertThat(reservation.status()).isEqualTo("CANCELLED");
    }

    @Test
    void setUpdatedAt_updatesTimestamp() {
        Instant newTime = NOW.plus(5, ChronoUnit.MINUTES);
        reservation.setUpdatedAt(newTime);
        assertThat(reservation.getUpdatedAt()).isEqualTo(newTime);
    }

    @Test
    void builder_withSingleItem_createsReservationWithThatItem() {
        var singleItem = new ReservationItem("SKU-A", 10);
        var res = Reservation.builder()
            .id(UUID.randomUUID())
            .orderId("ORD-SINGLE")
            .state(new PendingState())
            .item(singleItem)
            .createdAt(NOW)
            .updatedAt(NOW)
            .expiresAt(EXPIRES_AT)
            .build();

        assertThat(res.getItems()).hasSize(1);
        assertThat(res.getItems().get(0).sku()).isEqualTo("SKU-A");
    }

    @Test
    void builder_immutableItemsList() {
        var items = List.of(new ReservationItem("SKU-001", 5));
        var res = Reservation.builder()
            .id(UUID.randomUUID())
            .orderId("ORD-IMMUTABLE")
            .state(new PendingState())
            .items(items)
            .createdAt(NOW)
            .updatedAt(NOW)
            .expiresAt(EXPIRES_AT)
            .build();

        assertThatThrownBy(() -> res.getItems().add(new ReservationItem("SKU-002", 1)))
            .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void confirm_transitionsToConfirmedState() {
        reservation.confirm();
        assertThat(reservation.getState()).isInstanceOf(ConfirmedState.class);
    }

    @Test
    void cancel_transitionsToCancelledState() {
        reservation.cancel();
        assertThat(reservation.getState()).isInstanceOf(CancelledState.class);
    }
}
