package com.wirs.inventory.reservation.domain.factory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wirs.inventory.reservation.domain.model.Reservation;
import com.wirs.inventory.reservation.domain.model.ReservationItem;
import com.wirs.inventory.reservation.domain.state.PendingState;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
class ReservationFactoryTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-05-04T10:00:00Z");
    private static final int EXPIRY_MINUTES = 10;

    private Clock clock;
    private ReservationFactory factory;

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(FIXED_NOW, ZoneId.of("UTC"));
        factory = new ReservationFactory(clock, EXPIRY_MINUTES);
    }

    @Test
    void createPendingReservation_validInputs_returnsPendingReservation() {
        var items = List.of(
            new ReservationItem("SKU-001", 5),
            new ReservationItem("SKU-002", 3)
        );

        Reservation reservation = factory.createPendingReservation("ORD-123", items);

        assertThat(reservation.getOrderId()).isEqualTo("ORD-123");
        assertThat(reservation.getId()).isNotNull();
        assertThat(reservation.status()).isEqualTo("PENDING");
        assertThat(reservation.getState()).isInstanceOf(PendingState.class);
        assertThat(reservation.getItems()).hasSize(2);
        assertThat(reservation.getCreatedAt()).isEqualTo(FIXED_NOW);
        assertThat(reservation.getUpdatedAt()).isEqualTo(FIXED_NOW);
        assertThat(reservation.getExpiresAt()).isEqualTo(FIXED_NOW.plusSeconds(EXPIRY_MINUTES * 60));
    }

    @Test
    void createPendingReservation_nullOrderId_throwsIllegalArgumentException() {
        var items = List.of(new ReservationItem("SKU-001", 1));

        assertThatThrownBy(() -> factory.createPendingReservation(null, items))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Order ID must not be null or blank");
    }

    @Test
    void createPendingReservation_blankOrderId_throwsIllegalArgumentException() {
        var items = List.of(new ReservationItem("SKU-001", 1));

        assertThatThrownBy(() -> factory.createPendingReservation("  ", items))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Order ID must not be null or blank");
    }

    @Test
    void createPendingReservation_nullItems_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> factory.createPendingReservation("ORD-123", null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Items must not be null or empty");
    }

    @Test
    void createPendingReservation_emptyItems_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> factory.createPendingReservation("ORD-123", List.of()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Items must not be null or empty");
    }

    @Test
    void createPendingReservation_generatesDifferentIdsForEachCall() {
        var items = List.of(new ReservationItem("SKU-001", 1));

        Reservation r1 = factory.createPendingReservation("ORD-1", items);
        Reservation r2 = factory.createPendingReservation("ORD-2", items);

        assertThat(r1.getId()).isNotEqualTo(r2.getId());
    }

    @Test
    void createPendingReservation_expiryTimeIsNowPlusConfig() {
        var items = List.of(new ReservationItem("SKU-001", 1));

        Reservation reservation = factory.createPendingReservation("ORD-TTL", items);

        Instant expectedExpiry = FIXED_NOW.plusSeconds(EXPIRY_MINUTES * 60);
        assertThat(reservation.getExpiresAt()).isEqualTo(expectedExpiry);
    }
}
