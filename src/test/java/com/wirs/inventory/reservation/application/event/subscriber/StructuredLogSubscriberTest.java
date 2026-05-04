package com.wirs.inventory.reservation.application.event.subscriber;

import static org.assertj.core.api.Assertions.assertThatCode;

import com.wirs.inventory.reservation.domain.event.ReservationCancelledEvent;
import com.wirs.inventory.reservation.domain.event.ReservationConfirmedEvent;
import com.wirs.inventory.reservation.domain.event.ReservationCreatedEvent;
import com.wirs.inventory.reservation.domain.model.ReservationItem;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
class StructuredLogSubscriberTest {

    private final StructuredLogSubscriber subscriber = new StructuredLogSubscriber();
    private final UUID reservationId = UUID.randomUUID();

    @Test
    void on_reservationCreatedEvent_logsStructuredInfo() {
        var event = new ReservationCreatedEvent(reservationId, "ORD-SL-001",
            List.of(new ReservationItem("A100", 30)),
            Instant.now().plusSeconds(600), Instant.now());

        assertThatCode(() -> subscriber.on(event)).doesNotThrowAnyException();
    }

    @Test
    void on_reservationConfirmedEvent_logsStructuredInfo() {
        var event = new ReservationConfirmedEvent(reservationId, "ORD-SL-001", Instant.now());

        assertThatCode(() -> subscriber.on(event)).doesNotThrowAnyException();
    }

    @Test
    void on_reservationCancelledEvent_logsWithReason() {
        var event = new ReservationCancelledEvent(reservationId, "ORD-SL-001", "API", Instant.now());

        assertThatCode(() -> subscriber.on(event)).doesNotThrowAnyException();
    }
}
