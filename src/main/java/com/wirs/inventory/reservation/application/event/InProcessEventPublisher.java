package com.wirs.inventory.reservation.application.event;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Fans out domain events to all registered DomainEventSubscriber implementations. */
@Component
public class InProcessEventPublisher implements EventPublisher {

    private static final Logger log = LoggerFactory.getLogger(InProcessEventPublisher.class);

    private final List<DomainEventSubscriber> subscribers;

    /** Spring injects all DomainEventSubscriber beans via List injection. */
    public InProcessEventPublisher(List<DomainEventSubscriber> subscribers) {
        this.subscribers = List.copyOf(subscribers);
    }

    @Override
    public void publish(DomainEvent event) {
        log.debug("Fanned out event {} to {} subscriber(s)",
            event.eventType(), subscribers.size());
        subscribers.forEach(subscriber -> subscriber.on(event));
    }
}
