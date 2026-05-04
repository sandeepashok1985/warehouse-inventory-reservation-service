package com.wirs.inventory.reservation.application.relay;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.wirs.inventory.reservation.application.event.EventPublisher;
import com.wirs.inventory.reservation.domain.event.ReservationCreatedEvent;
import com.wirs.inventory.reservation.domain.model.ReservationItem;
import com.wirs.inventory.reservation.infrastructure.persistence.entity.ReservationEventEntity;
import com.wirs.inventory.reservation.infrastructure.persistence.repository.ReservationEventJpaRepository;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
class ReservationEventRelayTest {

    @Mock
    private ReservationEventJpaRepository eventRepository;

    @Mock
    private EventPublisher eventPublisher;

    private final ObjectMapper objectMapper =
        new ObjectMapper().registerModule(new JavaTimeModule());

    private ReservationEventRelay relay;

    @BeforeEach
    void setUp() {
        relay = new ReservationEventRelay(eventRepository, eventPublisher, objectMapper);
    }

    @Test
    void relayUnpublishedEvents_noUnpublishedEvents_doesNothing() {
        when(eventRepository.findTopNByPublishedAtIsNullOrderByCreatedAtAsc(50))
            .thenReturn(Collections.emptyList());

        relay.relayUnpublishedEvents();

        verify(eventPublisher, never()).publish(any());
        verify(eventRepository, never()).save(any());
    }

    @Test
    void relayUnpublishedEvents_oneEvent_dispatchesAndMarksPublished() throws Exception {
        var entity = new ReservationEventEntity();
        entity.setId(UUID.randomUUID());
        entity.setReservationId(UUID.randomUUID());
        entity.setEventType("CREATED");
        entity.setCreatedAt(Instant.now());
        var event = new ReservationCreatedEvent(entity.getReservationId(), "ORD-1",
            List.of(new ReservationItem("A100", 10)),
            Instant.now().plusSeconds(600), Instant.now());
        entity.setPayload(objectMapper.writeValueAsString(event));

        when(eventRepository.findTopNByPublishedAtIsNullOrderByCreatedAtAsc(50))
            .thenReturn(List.of(entity));

        relay.relayUnpublishedEvents();

        verify(eventPublisher).publish(any());
        var captor = ArgumentCaptor.forClass(ReservationEventEntity.class);
        verify(eventRepository).save(captor.capture());
        org.assertj.core.api.Assertions.assertThat(captor.getValue().getPublishedAt()).isNotNull();
    }

    @Test
    void relayUnpublishedEvents_publisherThrows_logsAndContinues() throws Exception {
        var entity1 = new ReservationEventEntity();
        entity1.setId(UUID.randomUUID());
        entity1.setReservationId(UUID.randomUUID());
        entity1.setEventType("CREATED");
        entity1.setCreatedAt(Instant.now());
        var event1 = new ReservationCreatedEvent(entity1.getReservationId(), "ORD-1",
            List.of(new ReservationItem("A100", 10)),
            Instant.now().plusSeconds(600), Instant.now());
        entity1.setPayload(objectMapper.writeValueAsString(event1));

        var entity2 = new ReservationEventEntity();
        entity2.setId(UUID.randomUUID());
        entity2.setReservationId(UUID.randomUUID());
        entity2.setEventType("CREATED");
        entity2.setCreatedAt(Instant.now());
        var event2 = new ReservationCreatedEvent(entity2.getReservationId(), "ORD-2",
            List.of(new ReservationItem("B200", 5)),
            Instant.now().plusSeconds(600), Instant.now());
        entity2.setPayload(objectMapper.writeValueAsString(event2));

        when(eventRepository.findTopNByPublishedAtIsNullOrderByCreatedAtAsc(50))
            .thenReturn(List.of(entity1, entity2));
        doThrow(new RuntimeException("fail"))
            .doNothing()
            .when(eventPublisher).publish(any());

        relay.relayUnpublishedEvents();

        // Only entity2 should be saved (entity1 failed, entity2 succeeded)
        verify(eventRepository).save(any());
    }

    @Test
    void relayUnpublishedEvents_unknownEventType_logsAndContinues() throws Exception {
        var entity = new ReservationEventEntity();
        entity.setId(UUID.randomUUID());
        entity.setEventType("UNKNOWN");
        entity.setPayload("{}");
        entity.setCreatedAt(Instant.now());

        when(eventRepository.findTopNByPublishedAtIsNullOrderByCreatedAtAsc(50))
            .thenReturn(List.of(entity));

        relay.relayUnpublishedEvents();

        verify(eventRepository, never()).save(any());
    }
}
