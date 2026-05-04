package com.wirs.inventory.reservation.infrastructure.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wirs.inventory.reservation.application.event.DomainEvent;
import com.wirs.inventory.reservation.application.event.DomainEventSubscriber;
import com.wirs.inventory.reservation.domain.event.ReservationCancelledEvent;
import com.wirs.inventory.reservation.domain.event.ReservationConfirmedEvent;
import com.wirs.inventory.reservation.domain.event.ReservationCreatedEvent;
import com.wirs.inventory.reservation.domain.event.ReservationEventType;
import io.nats.client.Connection;
import io.nats.client.JetStream;
import io.nats.client.JetStreamApiException;
import io.nats.client.PublishOptions;
import io.nats.client.api.PublishAck;
import java.io.IOException;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Publishes domain events to NATS JetStream subjects with server-side deduplication. */
@Component
@ConditionalOnProperty(name = "app.nats.enabled", havingValue = "true")
public class NatsEventPublisher implements DomainEventSubscriber, DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(NatsEventPublisher.class);

    private final JetStream jetStream;
    private final Connection natsConnection;
    private final ObjectMapper objectMapper;

    public NatsEventPublisher(JetStream jetStream, Connection natsConnection,
                               ObjectMapper objectMapper) {
        this.jetStream = jetStream;
        this.natsConnection = natsConnection;
        this.objectMapper = objectMapper;
    }

    @Override
    public void on(DomainEvent event) {
        String subject = toSubject(event.eventType());

        try {
            NatsPayload natsMsg = buildNatsMessage(event);
            byte[] payload = objectMapper.writeValueAsBytes(natsMsg);
            String messageId = event.aggregateId() + ":" + event.eventType();
            PublishOptions opts = PublishOptions.builder()
                .messageId(messageId)
                .build();

            PublishAck ack = jetStream.publish(subject, payload, opts);
            log.info("NATS publish OK: subject={}, seq={}, reservationId={}",
                subject, ack.getSeqno(), event.aggregateId());
        } catch (IOException | JetStreamApiException e) {
            log.warn("NATS publish failed for reservationId={}: {}", event.aggregateId(),
                e.getMessage());
        }
    }

    @Override
    public void destroy() {
        if (natsConnection.getStatus() != Connection.Status.CLOSED) {
            try {
                natsConnection.close();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private String toSubject(ReservationEventType eventType) {
        return switch (eventType) {
            case RESERVATION_CREATED   -> "reservations.created";
            case RESERVATION_CONFIRMED -> "reservations.confirmed";
            case RESERVATION_CANCELLED -> "reservations.cancelled";
        };
    }

    private NatsPayload buildNatsMessage(DomainEvent event) {
        String orderId = switch (event) {
            case ReservationCreatedEvent e   -> e.orderId();
            case ReservationConfirmedEvent e -> e.orderId();
            case ReservationCancelledEvent e -> e.orderId();
            default -> null;
        };
        String eventTypeName = event.eventType().name();
        return new NatsPayload(
            event.aggregateId() + ":" + eventTypeName,
            eventTypeName,
            event.aggregateId(),
            orderId,
            event.occurredAt(),
            event
        );
    }

    /** Internal payload record for NATS message serialization. */
    private record NatsPayload(
        String messageId,
        String eventType,
        UUID reservationId,
        String orderId,
        Instant occurredAt,
        Object payload
    ) {}
}
