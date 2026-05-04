package com.wirs.inventory.reservation.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.wirs.inventory.reservation.domain.event.ReservationCancelledEvent;
import com.wirs.inventory.reservation.domain.event.ReservationConfirmedEvent;
import com.wirs.inventory.reservation.domain.event.ReservationCreatedEvent;
import com.wirs.inventory.reservation.domain.model.ReservationItem;
import io.nats.client.Connection;
import io.nats.client.JetStream;
import io.nats.client.PublishOptions;
import io.nats.client.api.PublishAck;
import java.io.IOException;
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
class NatsEventPublisherTest {

    @Mock
    private JetStream jetStream;

    @Mock
    private Connection natsConnection;

    private final ObjectMapper objectMapper =
        new ObjectMapper().registerModule(new JavaTimeModule());

    private NatsEventPublisher publisher;

    private final UUID reservationId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        publisher = new NatsEventPublisher(jetStream, natsConnection, objectMapper);
    }

    @Test
    void on_reservationCreatedEvent_publishesToCorrectSubject() throws Exception {
        var pubAck = mock(PublishAck.class);
        doReturn(pubAck).when(jetStream)
            .publish(eq("reservations.created"), any(byte[].class), any(PublishOptions.class));

        var event = new ReservationCreatedEvent(reservationId, "ORD-1",
            List.of(new ReservationItem("A100", 30)),
            Instant.now().plusSeconds(600), Instant.now());

        publisher.on(event);

        var captor = ArgumentCaptor.forClass(PublishOptions.class);
        verify(jetStream).publish(eq("reservations.created"), any(byte[].class), captor.capture());
        assertThat(captor.getValue().getMessageId()).contains(reservationId.toString());
    }

    @Test
    void on_reservationConfirmedEvent_publishesToConfirmedSubject() throws Exception {
        var pubAck = mock(PublishAck.class);
        doReturn(pubAck).when(jetStream)
            .publish(eq("reservations.confirmed"), any(byte[].class), any(PublishOptions.class));

        publisher.on(new ReservationConfirmedEvent(reservationId, "ORD-1", Instant.now()));

        verify(jetStream).publish(eq("reservations.confirmed"), any(byte[].class),
            any(PublishOptions.class));
    }

    @Test
    void on_reservationCancelledEvent_publishesToCancelledSubject() throws Exception {
        var pubAck = mock(PublishAck.class);
        doReturn(pubAck).when(jetStream)
            .publish(eq("reservations.cancelled"), any(byte[].class), any(PublishOptions.class));

        publisher.on(new ReservationCancelledEvent(reservationId, "ORD-1", "API", Instant.now()));

        verify(jetStream).publish(eq("reservations.cancelled"), any(byte[].class),
            any(PublishOptions.class));
    }

    @Test
    void on_natsPublishThrowsIOException_doesNotRethrow() throws Exception {
        doThrow(new IOException("network error")).when(jetStream)
            .publish(any(String.class), any(byte[].class), any(PublishOptions.class));

        var event = new ReservationCreatedEvent(reservationId, "ORD-1",
            List.of(new ReservationItem("A100", 30)),
            Instant.now().plusSeconds(600), Instant.now());

        assertThatCode(() -> publisher.on(event)).doesNotThrowAnyException();
    }

    @Test
    void on_createsValidNatsSubject() throws Exception {
        var event = new ReservationCreatedEvent(reservationId, "ORD-1",
            List.of(new ReservationItem("A100", 30)),
            Instant.now().plusSeconds(600), Instant.now());

        var ack = mock(PublishAck.class);
        doReturn(1L).when(ack).getSeqno();
        doReturn(ack).when(jetStream).publish(
            any(String.class), any(byte[].class), any(PublishOptions.class));

        publisher.on(event);

        verify(jetStream).publish(eq("reservations.created"), any(byte[].class), any(PublishOptions.class));
    }
}
