package com.wirs.inventory.reservation.application.event;

/** Port: publishes a domain event to all registered subscribers. */
public interface EventPublisher {

    /** Distributes the event to all registered DomainEventSubscriber instances. */
    void publish(DomainEvent event);
}
