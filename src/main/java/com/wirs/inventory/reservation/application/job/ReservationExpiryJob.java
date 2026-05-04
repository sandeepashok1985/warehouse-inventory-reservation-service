package com.wirs.inventory.reservation.application.job;

import com.wirs.inventory.reservation.application.event.EventPublisher;
import com.wirs.inventory.reservation.application.service.InventoryService;
import com.wirs.inventory.reservation.domain.event.ReservationCancelledEvent;
import com.wirs.inventory.reservation.domain.model.Reservation;
import com.wirs.inventory.reservation.domain.model.ReservationItem;
import com.wirs.inventory.reservation.domain.state.CancelledState;
import com.wirs.inventory.reservation.domain.state.PendingState;
import com.wirs.inventory.reservation.domain.state.ReservationState;
import com.wirs.inventory.reservation.infrastructure.persistence.entity.ExpiryStateEntity;
import com.wirs.inventory.reservation.infrastructure.persistence.entity.ReservationEntity;
import com.wirs.inventory.reservation.infrastructure.persistence.repository.ExpiryStateJpaRepository;
import com.wirs.inventory.reservation.infrastructure.persistence.repository.ReservationEventJpaRepository;
import com.wirs.inventory.reservation.infrastructure.persistence.repository.ReservationJpaRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Scheduled job that cancels PENDING reservations whose TTL has elapsed.
 * Coordinates across multiple service instances via a single database row with pessimistic locking.
 */
@Component
public class ReservationExpiryJob {

    private static final Logger log = LoggerFactory.getLogger(ReservationExpiryJob.class);
    private static final int STUCK_FLAG_TIMEOUT_MINUTES = 5;

    private final ExpiryStateJpaRepository expiryStateRepository;
    private final ReservationJpaRepository reservationRepository;
    private final InventoryService inventoryService;
    private final ReservationEventJpaRepository eventRepository;
    private final EventPublisher eventPublisher;
    private final Clock clock;

    public ReservationExpiryJob(ExpiryStateJpaRepository expiryStateRepository,
                                 ReservationJpaRepository reservationRepository,
                                 InventoryService inventoryService,
                                 ReservationEventJpaRepository eventRepository,
                                 EventPublisher eventPublisher,
                                 Clock clock) {
        this.expiryStateRepository = expiryStateRepository;
        this.reservationRepository = reservationRepository;
        this.inventoryService = inventoryService;
        this.eventRepository = eventRepository;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    /**
     * Runs with a 2-minute fixed delay after each completion. SKIP LOCKED on the coordination row
     * means non-winning instances return immediately rather than queuing.
     */
    @Scheduled(fixedDelay = 120_000)
    @Transactional
    public void expireReservations() {
        Optional<ExpiryStateEntity> coordinationRow = expiryStateRepository.findCoordinationRowWithLock();
        if (coordinationRow.isEmpty()) {
            log.debug("Expiry job skipped — coordination row locked by another instance");
            return;
        }

        if (isBlockedByActiveJob(coordinationRow.get())) {
            log.info("Expiry job skipped — another instance is actively processing");
            return;
        }

        ExpiryStateEntity row = coordinationRow.get();
        row.setProcessingInProgress(true);
        row.setLastExpiryRun(Instant.now(clock));
        expiryStateRepository.save(row);

        try {
            processExpiredReservations();
        } finally {
            row.setProcessingInProgress(false);
            expiryStateRepository.save(row);
        }
    }

    /** Public to allow direct invocation in integration tests. */
    @Transactional
    public void processExpiredReservations() {
        List<ReservationEntity> expiredEntities = reservationRepository
            .findExpiredPendingReservations(Instant.now(clock));

        for (ReservationEntity entity : expiredEntities) {
            try {
                expireSingleReservation(entity);
            } catch (Exception e) {
                log.warn("Failed to expire reservation id={}: {}", entity.getId(), e.getMessage());
            }
        }
    }

    private void expireSingleReservation(ReservationEntity entity) {
        ReservationEntity lockedEntity = reservationRepository.findByIdWithLock(entity.getId()).orElse(null);
        if (lockedEntity == null || !PendingState.getName().equals(lockedEntity.getStatus())) {
            return;
        }
        Reservation reservation = toDomain(lockedEntity);
        reservation.cancel();

        List<ReservationItem> items = reservation.getItems();
        inventoryService.releaseStock(items);

        lockedEntity.updateStatus(CancelledState.getName(), Instant.now(clock));
        reservationRepository.save(lockedEntity);

        eventPublisher.publish(ReservationCancelledEvent.from(reservation, "TTL_EXPIRED", Instant.now(clock))); 
    }

    private boolean isBlockedByActiveJob(ExpiryStateEntity state) {
        if (!state.isProcessingInProgress()) {
            return false;
        }
        Instant stuckThreshold = Instant.now(clock)
            .minus(STUCK_FLAG_TIMEOUT_MINUTES, ChronoUnit.MINUTES);
        return state.getLastExpiryRun().isAfter(stuckThreshold);
    }

    private Reservation toDomain(ReservationEntity entity) {
        List<ReservationItem> items = entity.getItems().stream()
            .map(i -> new ReservationItem(i.getSku(), i.getQuantity()))
            .toList();
        return Reservation.builder()
            .id(entity.getId())
            .orderId(entity.getOrderId())
            .state(ReservationState.fromString(entity.getStatus()))
            .items(items)
            .createdAt(entity.getCreatedAt())
            .updatedAt(entity.getUpdatedAt())
            .expiresAt(entity.getExpiresAt())
            .build();
    }
}
