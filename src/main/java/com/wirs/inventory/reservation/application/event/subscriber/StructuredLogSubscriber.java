package com.wirs.inventory.reservation.application.event.subscriber;

import com.wirs.inventory.reservation.application.event.DomainEvent;
import com.wirs.inventory.reservation.application.event.DomainEventSubscriber;
import com.wirs.inventory.reservation.domain.event.ReservationCancelledEvent;
import com.wirs.inventory.reservation.domain.event.ReservationConfirmedEvent;
import com.wirs.inventory.reservation.domain.event.ReservationCreatedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Emits a structured log entry for every domain event for operational observability. */
@Component
public class StructuredLogSubscriber implements DomainEventSubscriber {

    private static final Logger log = LoggerFactory.getLogger(StructuredLogSubscriber.class);

    @Override
    public void on(DomainEvent event) {
        // Each event subtype carries different context for logging.
        // Pattern matching instanceof extracts the subtype-specific fields
        // (orderId, reason) without casting.
        if (event instanceof ReservationCreatedEvent created) {
            log.info("Reservation state transition: eventType={}, reservationId={}, orderId={}, "
                + "occurredAt={}, triggeredBy=API",
                event.eventType(), event.aggregateId(), created.orderId(), event.occurredAt());
        } else if (event instanceof ReservationConfirmedEvent confirmed) {
            log.info("Reservation state transition: eventType={}, reservationId={}, orderId={}, "
                + "occurredAt={}, triggeredBy=API",
                event.eventType(), event.aggregateId(), confirmed.orderId(), event.occurredAt());
        } else if (event instanceof ReservationCancelledEvent cancelled) {
            log.info("Reservation state transition: eventType={}, reservationId={}, orderId={}, "
                + "occurredAt={}, triggeredBy=API, reason={}",
                event.eventType(), event.aggregateId(), cancelled.orderId(),
                event.occurredAt(), cancelled.reason());
        } else {
            log.info("Reservation state transition: eventType={}, reservationId={}, occurredAt={}",
                event.eventType(), event.aggregateId(), event.occurredAt());
        }
    }
}
