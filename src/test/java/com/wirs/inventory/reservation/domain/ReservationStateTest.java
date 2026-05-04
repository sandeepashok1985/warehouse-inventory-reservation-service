package com.wirs.inventory.reservation.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wirs.inventory.reservation.domain.state.CancelledState;
import com.wirs.inventory.reservation.domain.state.ConfirmedState;
import com.wirs.inventory.reservation.domain.state.PendingState;
import com.wirs.inventory.reservation.domain.state.ReservationState;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
class ReservationStateTest {

    @Test
    void fromString_pending_returnsPendingState() {
        assertThat(ReservationState.fromString("PENDING")).isInstanceOf(PendingState.class);
    }

    @Test
    void fromString_confirmed_returnsConfirmedState() {
        assertThat(ReservationState.fromString("CONFIRMED")).isInstanceOf(ConfirmedState.class);
    }

    @Test
    void fromString_cancelled_returnsCancelledState() {
        assertThat(ReservationState.fromString("CANCELLED")).isInstanceOf(CancelledState.class);
    }

    @Test
    void fromString_unknownStatus_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> ReservationState.fromString("INVALID"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("INVALID");
    }
}
