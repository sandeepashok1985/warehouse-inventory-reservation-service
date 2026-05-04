package com.wirs.inventory.reservation.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wirs.inventory.reservation.domain.exception.InvalidStateTransitionException;
import com.wirs.inventory.reservation.domain.state.CancelledState;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
class CancelledStateTest {

    @Test
    void confirm_throwsInvalidStateTransitionException() {
        assertThatThrownBy(() -> new CancelledState().confirm())
            .isInstanceOf(InvalidStateTransitionException.class);
    }

    @Test
    void cancel_throwsInvalidStateTransitionException() {
        assertThatThrownBy(() -> new CancelledState().cancel())
            .isInstanceOf(InvalidStateTransitionException.class);
    }

    @Test
    void name_returnsCancelled() {
        assertThat(new CancelledState().name()).isEqualTo("CANCELLED");
    }
}
