package com.wirs.inventory.reservation.application.event.subscriber;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wirs.inventory.reservation.application.event.DomainEvent;
import com.wirs.inventory.reservation.application.event.DomainEventSubscriber;
import com.wirs.inventory.reservation.domain.event.ReservationEventType;
import com.wirs.inventory.reservation.infrastructure.persistence.entity.ReservationEventEntity;
import com.wirs.inventory.reservation.infrastructure.persistence.repository.ReservationEventJpaRepository;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Persists each domain event to the reservation_events outbox table for guaranteed delivery. */
@Component
public class AuditEventSubscriber implements DomainEventSubscriber {

    private static final Logger log = LoggerFactory.getLogger(AuditEventSubscriber.class);

    private final ReservationEventJpaRepository eventRepository;
    private final ObjectMapper objectMapper;

    public AuditEventSubscriber(ReservationEventJpaRepository eventRepository,
                                 ObjectMapper objectMapper) {
        this.eventRepository = eventRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    public void on(DomainEvent event) {
        try {
            String payload = objectMapper.writeValueAsString(event);
            ReservationEventEntity entity = new ReservationEventEntity();
            entity.setId(UUID.randomUUID());
            entity.setReservationId(event.aggregateId());
            entity.setEventType(abbreviateEventType(event.eventType()));
            entity.setPayload(payload);
            entity.setCreatedAt(Instant.now());
            entity.setPublishedAt(null);
            eventRepository.save(entity);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize event for audit: eventType={}, aggregateId={}",
                event.eventType(), event.aggregateId(), e);
        }
    }

    /** Shortens the enum name for storage in the {@code event_type} column (e.g. RESERVATION_CREATED → CREATED). */
    private String abbreviateEventType(ReservationEventType eventType) {
        return switch (eventType) {
            case RESERVATION_CREATED   -> "CREATED";
            case RESERVATION_CONFIRMED -> "CONFIRMED";
            case RESERVATION_CANCELLED -> "CANCELLED";
        };
    }
}
