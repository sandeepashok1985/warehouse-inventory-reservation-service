package com.wirs.inventory.reservation.infrastructure.persistence.repository;

import com.wirs.inventory.reservation.infrastructure.persistence.entity.ReservationEntity;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Spring Data JPA repository for reservations. */
public interface ReservationJpaRepository extends JpaRepository<ReservationEntity, UUID> {

    /** Finds a reservation by its external order reference. */
    Optional<ReservationEntity> findByOrderId(String orderId);

    /** Returns a page of reservations filtered by status. */
    Page<ReservationEntity> findByStatus(String status, Pageable pageable);

    /**
     * Locks a reservation row for update (pessimistic write lock).
     * Used for confirm/cancel state transitions.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM ReservationEntity r WHERE r.id = :id")
    Optional<ReservationEntity> findByIdWithLock(@Param("id") UUID id);

    /** Returns PENDING reservations whose TTL has elapsed. Used by the expiry job. */
    @Query("SELECT r FROM ReservationEntity r WHERE r.status = 'PENDING' AND r.expiresAt < :now")
    List<ReservationEntity> findExpiredPendingReservations(@Param("now") Instant now);
}
