package com.wirs.inventory.reservation.application;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.wirs.inventory.reservation.application.event.DomainEvent;
import com.wirs.inventory.reservation.application.event.DomainEventSubscriber;
import com.wirs.inventory.reservation.application.event.InProcessEventPublisher;
import com.wirs.inventory.reservation.domain.event.ReservationCreatedEvent;
import com.wirs.inventory.reservation.domain.model.ReservationItem;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
class InProcessEventPublisherTest {

    private DomainEvent sampleEvent() {
        return new ReservationCreatedEvent(
            UUID.randomUUID(), "ORD-1",
            List.of(new ReservationItem("A100", 5)),
            Instant.now().plusSeconds(600), Instant.now());
    }

    @Test
    void publish_fansOutToAllSubscribers() {
        DomainEventSubscriber sub1 = mock(DomainEventSubscriber.class);
        DomainEventSubscriber sub2 = mock(DomainEventSubscriber.class);
        InProcessEventPublisher publisher = new InProcessEventPublisher(List.of(sub1, sub2));
        DomainEvent event = sampleEvent();

        publisher.publish(event);

        verify(sub1, times(1)).on(event);
        verify(sub2, times(1)).on(event);
    }

    @Test
    void publish_withNoSubscribers_completesWithoutException() {
        InProcessEventPublisher publisher = new InProcessEventPublisher(List.of());
        assertDoesNotThrow(() -> publisher.publish(sampleEvent()));
    }
}
