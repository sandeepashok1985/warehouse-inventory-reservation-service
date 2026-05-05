package com.wirs.inventory.reservation.infrastructure.persistence.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
class ReservationEntityTest {

    private ReservationEntity entity;

    @BeforeEach
    void setUp() {
        entity = new ReservationEntity();
        entity.setId(UUID.randomUUID());
        entity.setOrderId("ORD-001");
        entity.setStatus("PENDING");
    }

    @Test
    void prePersist_setsCreatedAtAndUpdatedAtWhenNull() {
        entity.setCreatedAt(null);
        entity.setUpdatedAt(null);

        entity.prePersist();

        assertThat(entity.getCreatedAt()).isNotNull();
        assertThat(entity.getUpdatedAt()).isNotNull();
    }

    @Test
    void prePersist_doesNotOverrideExistingCreatedAt() {
        Instant past = Instant.now().minus(1, ChronoUnit.HOURS);
        entity.setCreatedAt(past);
        entity.setUpdatedAt(null);

        entity.prePersist();

        assertThat(entity.getCreatedAt()).isEqualTo(past);
        assertThat(entity.getUpdatedAt()).isNotNull();
    }

    @Test
    void preUpdate_setsUpdatedAt() {
        Instant past = Instant.now().minusSeconds(60);
        entity.setUpdatedAt(past);
        entity.setCreatedAt(past);

        entity.preUpdate();

        assertThat(entity.getUpdatedAt()).isAfter(past);
    }

    @Test
    void updateStatus_setsStatusAndUpdatedAt() {
        Instant now = Instant.now();
        entity.setCreatedAt(now.minus(1, ChronoUnit.HOURS));
        entity.setUpdatedAt(now.minus(1, ChronoUnit.HOURS));

        entity.updateStatus("CONFIRMED", now);

        assertThat(entity.getStatus()).isEqualTo("CONFIRMED");
        assertThat(entity.getUpdatedAt()).isEqualTo(now);
    }

    @Test
    void updateStatus_toCancelled_setsStatusAndUpdatedAt() {
        Instant now = Instant.now();

        entity.updateStatus("CANCELLED", now);

        assertThat(entity.getStatus()).isEqualTo("CANCELLED");
        assertThat(entity.getUpdatedAt()).isEqualTo(now);
    }

    @Test
    void entity_initializedWithEmptyItemsList() {
        assertThat(entity.getItems()).isNotNull();
        assertThat(entity.getItems()).isEmpty();
    }
}
