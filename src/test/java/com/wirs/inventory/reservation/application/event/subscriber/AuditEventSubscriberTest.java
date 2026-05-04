package com.wirs.inventory.reservation.application.event.subscriber;

import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.wirs.inventory.reservation.domain.event.ReservationCancelledEvent;
import com.wirs.inventory.reservation.domain.event.ReservationConfirmedEvent;
import com.wirs.inventory.reservation.domain.event.ReservationCreatedEvent;
import com.wirs.inventory.reservation.domain.model.ReservationItem;
import com.wirs.inventory.reservation.infrastructure.persistence.entity.ReservationEventEntity;
import com.wirs.inventory.reservation.infrastructure.persistence.repository.ReservationEventJpaRepository;
import java.time.Instant;
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
class AuditEventSubscriberTest {

    @Mock
    private ReservationEventJpaRepository eventRepository;

    private final ObjectMapper objectMapper =
        new ObjectMapper().registerModule(new JavaTimeModule());

    private AuditEventSubscriber subscriber;

    private final UUID reservationId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        subscriber = new AuditEventSubscriber(eventRepository, objectMapper);
    }

    @Test
    void on_reservationCreatedEvent_persistsWithCorrectEventType() throws Exception {
        var event = new ReservationCreatedEvent(reservationId, "ORD-1",
            List.of(new ReservationItem("A100", 30)),
            Instant.now().plusSeconds(600), Instant.now());

        subscriber.on(event);

        var captor = ArgumentCaptor.forClass(ReservationEventEntity.class);
        verify(eventRepository).save(captor.capture());
        var entity = captor.getValue();
        org.assertj.core.api.Assertions.assertThat(entity.getEventType()).isEqualTo("CREATED");
        org.assertj.core.api.Assertions.assertThat(entity.getPublishedAt()).isNull();
        org.assertj.core.api.Assertions.assertThat(entity.getReservationId()).isEqualTo(reservationId);
    }

    @Test
    void on_reservationConfirmedEvent_persistsWithCorrectEventType() throws Exception {
        var event = new ReservationConfirmedEvent(reservationId, "ORD-1", Instant.now());

        subscriber.on(event);

        var captor = ArgumentCaptor.forClass(ReservationEventEntity.class);
        verify(eventRepository).save(captor.capture());
        org.assertj.core.api.Assertions.assertThat(captor.getValue().getEventType())
            .isEqualTo("CONFIRMED");
    }

    @Test
    void on_reservationCancelledEvent_persistsWithCorrectEventType() throws Exception {
        var event = new ReservationCancelledEvent(reservationId, "ORD-1", "API", Instant.now());

        subscriber.on(event);

        var captor = ArgumentCaptor.forClass(ReservationEventEntity.class);
        verify(eventRepository).save(captor.capture());
        org.assertj.core.api.Assertions.assertThat(captor.getValue().getEventType())
            .isEqualTo("CANCELLED");
    }
}
