package com.wirs.inventory.reservation.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.wirs.inventory.reservation.application.relay.ReservationEventRelay;
import com.wirs.inventory.reservation.infrastructure.persistence.entity.ReservationEventEntity;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Verifies the core outbox relay behavior using PostgreSQL via Testcontainers.
 * NATS-specific integration tests (Chunk 20) require a running NATS server.
 */
@Tag("integration")
class OutboxRelayIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private ReservationEventRelay coreRelay;

    @Test
    void relayUnpublishedEvents_eventMarkedPublished() {
        // Insert an unpublished event directly
        var entity = new ReservationEventEntity();
        entity.setId(UUID.randomUUID());
        entity.setReservationId(UUID.randomUUID());
        entity.setEventType("CREATED");
        entity.setCreatedAt(Instant.now());
        entity.setPayload("{\"reservationId\":\"" + entity.getReservationId()
            + "\",\"orderId\":\"ORD-RELAY\",\"eventType\":\"RESERVATION_CREATED\",\"occurredAt\":\""
            + Instant.now() + "\"}");
        eventRepository.save(entity);

        // Run core relay
        coreRelay.relayUnpublishedEvents();

        // Published_at should be non-null after relay
        var updated = eventRepository.findById(entity.getId()).orElseThrow();
        assertThat(updated.getPublishedAt()).isNotNull();
    }

    @Test
    void relayUnpublishedEvents_unknownEventType_notMarkedPublished() {
        var entity = new ReservationEventEntity();
        entity.setId(UUID.randomUUID());
        entity.setReservationId(UUID.randomUUID());
        entity.setEventType("UNKNOWN");
        entity.setCreatedAt(Instant.now());
        entity.setPayload("{}");
        eventRepository.save(entity);

        coreRelay.relayUnpublishedEvents();

        var updated = eventRepository.findById(entity.getId()).orElseThrow();
        assertThat(updated.getPublishedAt()).isNull();
    }
}
