package com.wirs.inventory.reservation.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.wirs.inventory.reservation.domain.state.CancelledState;
import com.wirs.inventory.reservation.domain.state.ConfirmedState;
import com.wirs.inventory.reservation.domain.state.PendingState;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
class PendingStateTest {

    @Test
    void confirm_returnConfirmedState() {
        assertThat(new PendingState().confirm()).isInstanceOf(ConfirmedState.class);
    }

    @Test
    void cancel_returnsCancelledState() {
        assertThat(new PendingState().cancel()).isInstanceOf(CancelledState.class);
    }

    @Test
    void name_returnsPending() {
        assertThat(new PendingState().name()).isEqualTo("PENDING");
    }
}
