package com.wirs.inventory.reservation.infrastructure.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wirs.inventory.reservation.application.event.DomainEvent;
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
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Outbox relay — re-publishes events that were not delivered in the request path to NATS. */
@Component
@ConditionalOnProperty(name = "app.nats.enabled", havingValue = "true")
public class NatsOutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(NatsOutboxRelay.class);

    private final ReservationEventJpaRepository eventRepository;
    private final NatsEventPublisher natsPublisher;
    private final ObjectMapper objectMapper;

    public NatsOutboxRelay(ReservationEventJpaRepository eventRepository,
                            NatsEventPublisher natsPublisher,
                            ObjectMapper objectMapper) {
        this.eventRepository = eventRepository;
        this.natsPublisher = natsPublisher;
        this.objectMapper = objectMapper;
    }

    /**
     * Polls for unpublished events every 30 seconds and re-delivers them to NATS.
     * Processes up to 50 events per run to avoid overwhelming the NATS cluster after an outage.
     */
    @Scheduled(fixedDelay = 30_000)
    @Transactional
    public void relayUnpublishedEvents() {
        List<ReservationEventEntity> unpublished =
            eventRepository.findTop50ByPublishedAtIsNullOrderByCreatedAtAsc();
        if (unpublished.isEmpty()) {
            return;
        }
        log.info("Outbox relay: {} events pending", unpublished.size());

        for (ReservationEventEntity entity : unpublished) {
            try {
                DomainEvent event = reconstruct(entity);
                natsPublisher.on(event);
                entity.setPublishedAt(Instant.now());
                eventRepository.save(entity);
            } catch (Exception e) {
                log.warn("Relay failed for event id={}: {}", entity.getId(), e.getMessage());
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
    private DomainEvent reconstruct(ReservationEventEntity entity) throws IOException {
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
