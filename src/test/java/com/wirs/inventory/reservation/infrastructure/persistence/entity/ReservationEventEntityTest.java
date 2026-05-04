package com.wirs.inventory.reservation.infrastructure.persistence.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
class ReservationEventEntityTest {

    @Test
    void prePersist_setsIdAndCreatedAt() {
        ReservationEventEntity entity = new ReservationEventEntity();
        entity.setReservationId(UUID.randomUUID());
        entity.setEventType("CREATED");
        entity.setPayload("{}");

        entity.prePersist();

        assertThat(entity.getId()).isNotNull();
        assertThat(entity.getCreatedAt()).isNotNull();
    }

    @Test
    void prePersist_doesNotOverwriteExistingValues() {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        ReservationEventEntity entity = new ReservationEventEntity();
        entity.setId(id);
        entity.setCreatedAt(now);
        entity.setReservationId(UUID.randomUUID());
        entity.setEventType("CONFIRMED");
        entity.setPayload("{}");

        entity.prePersist();

        assertThat(entity.getId()).isEqualTo(id);
        assertThat(entity.getCreatedAt()).isEqualTo(now);
    }
}
