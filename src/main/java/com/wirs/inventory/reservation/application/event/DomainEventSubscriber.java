package com.wirs.inventory.reservation.application.event;

/** Port: receives domain events for processing. */
public interface DomainEventSubscriber {

    /** Handles a domain event. Implementations must not throw checked exceptions. */
    void on(DomainEvent event);
}
