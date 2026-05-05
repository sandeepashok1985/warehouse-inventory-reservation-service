package com.wirs.inventory.reservation.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * JPA entity mapping the {@code reservation_events} outbox table.
 *
 * Stores every domain event as a JSONB payload for guaranteed delivery via
 * the Transactional Outbox pattern. Events are inserted atomically within the
 * same database transaction as the state change they describe. A background
 * relay polls for rows where {@code published_at IS NULL} and dispatches them
 * to NATS JetStream or in-process subscribers.
 */
@Entity
@Table(name = "reservation_events")
@Getter
@Setter
@NoArgsConstructor
public class ReservationEventEntity {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "reservation_id", nullable = false)
    private UUID reservationId;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false, columnDefinition = "jsonb")
    private String payload;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    @PrePersist
    void prePersist() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
