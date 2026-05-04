package com.wirs.inventory.reservation.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wirs.inventory.reservation.domain.exception.InvalidStateTransitionException;
import com.wirs.inventory.reservation.domain.state.ConfirmedState;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
class ConfirmedStateTest {

    @Test
    void confirm_throwsInvalidStateTransitionException() {
        assertThatThrownBy(() -> new ConfirmedState().confirm())
            .isInstanceOf(InvalidStateTransitionException.class);
    }

    @Test
    void cancel_throwsInvalidStateTransitionException() {
        assertThatThrownBy(() -> new ConfirmedState().cancel())
            .isInstanceOf(InvalidStateTransitionException.class);
    }

    @Test
    void name_returnsConfirmed() {
        assertThat(new ConfirmedState().name()).isEqualTo("CONFIRMED");
    }
}
