package com.wirs.inventory.reservation.infrastructure.persistence.repository;

import com.wirs.inventory.reservation.infrastructure.persistence.entity.ExpiryStateEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** Spring Data JPA repository for the expiry coordination singleton row. */
public interface ExpiryStateJpaRepository extends JpaRepository<ExpiryStateEntity, Integer> {

    /**
     * Attempts a non-blocking lock on the coordination row via SKIP LOCKED.
     * Returns empty if the row is already locked by another instance — callers skip immediately.
     * SKIP LOCKED requires a native query; blocking FOR UPDATE would queue all non-winning instances.
     */
    @Query(
        value = "SELECT * FROM reservation_expiry_state WHERE id = 1 FOR UPDATE SKIP LOCKED",
        nativeQuery = true
    )
    Optional<ExpiryStateEntity> findCoordinationRowWithLock();
}
