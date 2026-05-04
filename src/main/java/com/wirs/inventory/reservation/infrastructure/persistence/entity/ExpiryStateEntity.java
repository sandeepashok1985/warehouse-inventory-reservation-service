package com.wirs.inventory.reservation.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** JPA entity mapping the reservation_expiry_state coordination row (always id=1). */
@Entity
@Table(name = "reservation_expiry_state")
@Getter
@Setter
@NoArgsConstructor
public class ExpiryStateEntity {

    @Id
    @Column(name = "id")
    private int id;

    @Column(name = "last_expiry_run", nullable = false)
    private Instant lastExpiryRun;

    @Column(name = "processing_in_progress", nullable = false)
    private boolean processingInProgress;
}
