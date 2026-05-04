package com.wirs.inventory.reservation.infrastructure.messaging;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
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
class NatsOutboxRelayTest {

    @Mock
    private ReservationEventJpaRepository eventRepository;

    @Mock
    private NatsEventPublisher natsPublisher;

    private final ObjectMapper objectMapper =
        new ObjectMapper().registerModule(new JavaTimeModule());

    private NatsOutboxRelay relay;

    @BeforeEach
    void setUp() {
        relay = new NatsOutboxRelay(eventRepository, natsPublisher, objectMapper);
    }

    @Test
    void relayUnpublishedEvents_noEvents_doesNothing() {
        when(eventRepository.findTop50ByPublishedAtIsNullOrderByCreatedAtAsc())
            .thenReturn(Collections.emptyList());

        relay.relayUnpublishedEvents();

        verify(natsPublisher, never()).on(any());
        verify(eventRepository, never()).save(any());
    }

    @Test
    void relayUnpublishedEvents_oneEvent_publishesAndSetsPublishedAt() throws Exception {
        var entity = new ReservationEventEntity();
        entity.setId(UUID.randomUUID());
        entity.setReservationId(UUID.randomUUID());
        entity.setEventType("CREATED");
        entity.setCreatedAt(Instant.now());
        var event = new ReservationCreatedEvent(entity.getReservationId(), "ORD-1",
            List.of(new ReservationItem("A100", 10)),
            Instant.now().plusSeconds(600), Instant.now());
        entity.setPayload(objectMapper.writeValueAsString(event));

        when(eventRepository.findTop50ByPublishedAtIsNullOrderByCreatedAtAsc())
            .thenReturn(List.of(entity));

        relay.relayUnpublishedEvents();

        verify(natsPublisher).on(any());
        var captor = ArgumentCaptor.forClass(ReservationEventEntity.class);
        verify(eventRepository).save(captor.capture());
        org.assertj.core.api.Assertions.assertThat(captor.getValue().getPublishedAt()).isNotNull();
    }

    @Test
    void relayUnpublishedEvents_publishFails_continuesAndLogs() throws Exception {
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

        when(eventRepository.findTop50ByPublishedAtIsNullOrderByCreatedAtAsc())
            .thenReturn(List.of(entity1, entity2));

        // First call throws, second succeeds
        org.mockito.Mockito.doThrow(new RuntimeException("fail"))
            .doNothing()
            .when(natsPublisher).on(any());

        relay.relayUnpublishedEvents();

        // Both entities attempted, only entity2 saved
        verify(natsPublisher, times(2)).on(any());
        verify(eventRepository).save(any());
    }
}
