package com.wirs.inventory.reservation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Minimal smoke test for Chunk 1 — verifies the application class can be instantiated. */
@Tag("integration")
class ReservationServiceApplicationTest {

    @Test
    void instantiateMainClass_doesNotThrow() {
        // Verify the main application class can be instantiated.
        // Full Spring context load will be verified in Chunk 16 integration tests.
        ReservationServiceApplication app = new ReservationServiceApplication();
        assertThat(app).isNotNull();
    }
}
