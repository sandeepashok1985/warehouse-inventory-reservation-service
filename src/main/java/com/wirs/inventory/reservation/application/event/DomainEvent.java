package com.wirs.inventory.reservation.application.event;

import com.wirs.inventory.reservation.domain.event.ReservationEventType;
import java.time.Instant;
import java.util.UUID;

/** Marker interface for all domain events emitted by this service. */
public interface DomainEvent {

    /** The reservation UUID that this event relates to. */
    UUID aggregateId();

    /** When the event occurred, in UTC. */
    Instant occurredAt();

    /** The event type key. */
    ReservationEventType eventType();
}
