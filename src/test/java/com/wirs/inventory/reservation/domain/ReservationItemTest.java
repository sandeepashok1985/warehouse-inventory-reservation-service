package com.wirs.inventory.reservation.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import com.wirs.inventory.reservation.domain.model.ReservationItem;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
class ReservationItemTest {

    @Test
    void nullSku_throwsNullPointerException() {
        assertThatThrownBy(() -> new ReservationItem(null, 1))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void blankSku_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> new ReservationItem("  ", 1))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void zeroQuantity_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> new ReservationItem("A100", 0))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("A100");
    }

    @Test
    void negativeQuantity_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> new ReservationItem("A100", -5))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void validItem_constructsSuccessfully() {
        assertDoesNotThrow(() -> {
            var item = new ReservationItem("A100", 10);
            assertThat(item.sku()).isEqualTo("A100");
            assertThat(item.quantity()).isEqualTo(10);
        });
    }
}
