package com.wirs.inventory.reservation.infrastructure.persistence.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * JPA entity mapping the {@code reservations} table.
 *
 * This is the primary persistence root for the {@link com.wirs.inventory.reservation.domain.model.Reservation}
 * aggregate. Items are stored in a separate {@code reservation_items} table and
 * loaded lazily via a {@link jakarta.persistence.OneToMany} relationship.
 *
 * Lifecycle callbacks ({@code prePersist}, {@code preUpdate}) automatically
 * manage the {@code created_at} and {@code updated_at} timestamps.
 */
@Entity
@Table(name = "reservations")
@Getter
@Setter
@NoArgsConstructor
public class ReservationEntity {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "order_id", nullable = false, unique = true)
    private String orderId;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @OneToMany(mappedBy = "reservation", cascade = CascadeType.ALL,
               orphanRemoval = true, fetch = FetchType.LAZY)
    private List<ReservationItemEntity> items = new ArrayList<>();

    /** Sets audit timestamps before the first persist. */
    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (updatedAt == null) {
            updatedAt = Instant.now();
        }
    }

    /** Refreshes the updated-at timestamp before every entity update. */
    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }

    /**
     * Updates the reservation status and sets the updated-at timestamp atomically.
     *
     * @param newStatus the new status string (matches the DB CHECK constraint values)
     * @param now       the current instant (provided by the caller to align with the
     *                  application-level clock)
     */
    public void updateStatus(String newStatus, Instant now) {
        this.status = newStatus;
        this.updatedAt = now;
    }
}
