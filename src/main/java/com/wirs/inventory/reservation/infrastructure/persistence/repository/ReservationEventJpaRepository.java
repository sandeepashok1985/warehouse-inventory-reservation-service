package com.wirs.inventory.reservation.infrastructure.persistence.repository;

import com.wirs.inventory.reservation.infrastructure.persistence.entity.ReservationEventEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Spring Data JPA repository for the reservation_events outbox table. */
public interface ReservationEventJpaRepository
        extends JpaRepository<ReservationEventEntity, UUID> {

    /** Returns up to 50 unpublished events, oldest first. Used by the outbox relay. */
    List<ReservationEventEntity> findTop50ByPublishedAtIsNullOrderByCreatedAtAsc();

    /**
     * Returns up to limit undelivered events, oldest first.
     * The partial index on (created_at) WHERE published_at IS NULL makes this query efficient.
     */
    @Query("SELECT e FROM ReservationEventEntity e WHERE e.publishedAt IS NULL "
        + "ORDER BY e.createdAt ASC LIMIT :limit")
    List<ReservationEventEntity> findTopNByPublishedAtIsNullOrderByCreatedAtAsc(
        @Param("limit") int limit);
}
