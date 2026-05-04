package com.wirs.inventory.reservation.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
class ReservationExpiryConfigTest {

    @Test
    void bindsExpiryMinutes() {
        ReservationExpiryConfig config = new ReservationExpiryConfig(30);
        assertThat(config.expiryMinutes()).isEqualTo(30);
    }
}
