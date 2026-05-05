package com.wirs.inventory.reservation.application.relay;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wirs.inventory.reservation.application.event.DomainEvent;
import com.wirs.inventory.reservation.application.event.EventPublisher;
import com.wirs.inventory.reservation.domain.event.ReservationCancelledEvent;
import com.wirs.inventory.reservation.domain.event.ReservationConfirmedEvent;
import com.wirs.inventory.reservation.domain.event.ReservationCreatedEvent;
import com.wirs.inventory.reservation.infrastructure.persistence.entity.ReservationEventEntity;
import com.wirs.inventory.reservation.infrastructure.persistence.repository.ReservationEventJpaRepository;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Polls reservation_events for undelivered events and dispatches them to in-process subscribers.
 * Acts as the durability backstop for the Transactional Outbox pattern.
 */
@Component
public class ReservationEventRelay {

    private static final Logger log = LoggerFactory.getLogger(ReservationEventRelay.class);
    private static final int BATCH_SIZE = 50;

    private final ReservationEventJpaRepository eventRepository;
    private final EventPublisher eventPublisher;
    private final ObjectMapper objectMapper;

    public ReservationEventRelay(ReservationEventJpaRepository eventRepository,
                                  EventPublisher eventPublisher,
                                  ObjectMapper objectMapper) {
        this.eventRepository = eventRepository;
        this.eventPublisher = eventPublisher;
        this.objectMapper = objectMapper;
    }

    /**
     * Runs every 30 seconds. Finds up to 50 oldest undelivered events, dispatches each,
     * and marks published_at on success. On subscriber failure, logs and continues.
     */
    @Scheduled(fixedDelay = 30_000)
    @Transactional
    public void relayUnpublishedEvents() {
        List<ReservationEventEntity> unpublished = eventRepository
            .findTopNByPublishedAtIsNullOrderByCreatedAtAsc(BATCH_SIZE);

        if (unpublished.isEmpty()) {
            return;
        }

        log.debug("Relay found {} undelivered event(s)", unpublished.size());

        for (ReservationEventEntity eventEntity : unpublished) {
            try {
                DomainEvent domainEvent = deserialize(eventEntity);
                eventPublisher.publish(domainEvent);
                eventEntity.setPublishedAt(Instant.now());
                eventRepository.save(eventEntity);
            } catch (Exception e) {
                log.warn("Relay failed for eventId={}, type={}: {}",
                    eventEntity.getId(), eventEntity.getEventType(), e.getMessage());
            }
        }
    }

    /**
     * Deserializes a {@link ReservationEventEntity} payload back into the correct
     * {@link DomainEvent} subtype based on the stored {@code event_type}.
     *
     * @param entity the persisted event entity (must not be null)
     * @return the reconstructed domain event
     * @throws IOException if JSON deserialisation fails
     * @throws IllegalArgumentException if the event type is unknown
     */
    private DomainEvent deserialize(ReservationEventEntity entity) throws IOException {
        return switch (entity.getEventType()) {
            case "CREATED" ->
                objectMapper.readValue(entity.getPayload(), ReservationCreatedEvent.class);
            case "CONFIRMED" ->
                objectMapper.readValue(entity.getPayload(), ReservationConfirmedEvent.class);
            case "CANCELLED" ->
                objectMapper.readValue(entity.getPayload(), ReservationCancelledEvent.class);
            default -> throw new IllegalArgumentException(
                "Unknown event type: " + entity.getEventType());
        };
    }
}
