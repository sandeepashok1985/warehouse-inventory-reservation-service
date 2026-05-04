package com.wirs.inventory.reservation.application;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wirs.inventory.reservation.application.event.EventPublisher;
import com.wirs.inventory.reservation.application.job.ReservationExpiryJob;
import com.wirs.inventory.reservation.application.service.InventoryService;
import com.wirs.inventory.reservation.domain.event.ReservationCancelledEvent;
import com.wirs.inventory.reservation.infrastructure.persistence.entity.ExpiryStateEntity;
import com.wirs.inventory.reservation.infrastructure.persistence.entity.ReservationEntity;
import com.wirs.inventory.reservation.infrastructure.persistence.entity.ReservationItemEntity;
import com.wirs.inventory.reservation.infrastructure.persistence.repository.ExpiryStateJpaRepository;
import com.wirs.inventory.reservation.infrastructure.persistence.repository.ReservationEventJpaRepository;
import com.wirs.inventory.reservation.infrastructure.persistence.repository.ReservationJpaRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
class ReservationExpiryJobTest {

    @Mock private ExpiryStateJpaRepository expiryStateRepository;
    @Mock private ReservationJpaRepository reservationRepository;
    @Mock private InventoryService inventoryService;
    @Mock private ReservationEventJpaRepository eventRepository;
    @Mock private EventPublisher eventPublisher;
    @Mock private Clock clock;

    @InjectMocks
    private ReservationExpiryJob job;

    private static final Instant NOW = Instant.parse("2026-05-01T12:00:00Z");

    private void stubClock() {
        when(clock.instant()).thenReturn(NOW);
    }

    private ExpiryStateEntity buildCoordRow(boolean inProgress, Instant lastRun) {
        var row = new ExpiryStateEntity();
        row.setId(1);
        row.setProcessingInProgress(inProgress);
        row.setLastExpiryRun(lastRun);
        return row;
    }

    private ReservationEntity buildPendingEntity() {
        var entity = new ReservationEntity();
        entity.setId(UUID.randomUUID());
        entity.setOrderId("ORD-1");
        entity.setStatus("PENDING");
        entity.setCreatedAt(NOW.minusSeconds(3600));
        entity.setUpdatedAt(NOW.minusSeconds(3600));
        entity.setExpiresAt(NOW.minusSeconds(600));
        var item = new ReservationItemEntity();
        item.setId(UUID.randomUUID());
        item.setSku("A100");
        item.setQuantity(30L);
        item.setReservation(entity);
        entity.setItems(new ArrayList<>(List.of(item)));
        return entity;
    }

    @Test
    void expireReservations_processingInProgress_recentRun_skipsExecution() {
        stubClock();
        var row = buildCoordRow(true, NOW.minusSeconds(120));
        when(expiryStateRepository.findCoordinationRowWithLock()).thenReturn(Optional.of(row));

        job.expireReservations();

        verify(reservationRepository, never()).findExpiredPendingReservations(any());
    }

    @Test
    void expireReservations_processingInProgress_staleRun_overridesAndProcesses() {
        stubClock();
        var row = buildCoordRow(true, NOW.minusSeconds(600));
        when(expiryStateRepository.findCoordinationRowWithLock()).thenReturn(Optional.of(row));
        when(reservationRepository.findExpiredPendingReservations(any())).thenReturn(List.of());

        job.expireReservations();

        verify(expiryStateRepository, atLeastOnce()).save(any());
    }

    @Test
    void expireReservations_expiredReservation_cancelledAndStockReleased() {
        stubClock();
        var row = buildCoordRow(false, NOW.minusSeconds(300));
        when(expiryStateRepository.findCoordinationRowWithLock()).thenReturn(Optional.of(row));
        var entity = buildPendingEntity();
        when(reservationRepository.findExpiredPendingReservations(any()))
            .thenReturn(List.of(entity));
        when(reservationRepository.findByIdWithLock(entity.getId()))
            .thenReturn(Optional.of(entity));
        when(reservationRepository.save(any())).thenReturn(entity);

        job.expireReservations();

        verify(inventoryService).releaseStock(any());
        verify(reservationRepository).save(argThat(e -> "CANCELLED".equals(e.getStatus())));
        verify(eventPublisher).publish(argThat(
            e -> e instanceof ReservationCancelledEvent re && "TTL_EXPIRED".equals(re.reason())));
    }

    @Test
    void expireReservations_reservationAlreadyConfirmed_skipsGracefully() {
        stubClock();
        var row = buildCoordRow(false, NOW.minusSeconds(300));
        when(expiryStateRepository.findCoordinationRowWithLock()).thenReturn(Optional.of(row));
        var entity = buildPendingEntity();
        when(reservationRepository.findExpiredPendingReservations(any()))
            .thenReturn(List.of(entity));
        var confirmedEntity = buildPendingEntity();
        confirmedEntity.setStatus("CONFIRMED");
        when(reservationRepository.findByIdWithLock(entity.getId()))
            .thenReturn(Optional.of(confirmedEntity));

        job.expireReservations();

        verify(inventoryService, never()).releaseStock(any());
    }

    @Test
    void expireReservations_processingFlagClearedInFinallyBlock() {
        stubClock();
        var row = buildCoordRow(false, NOW.minusSeconds(300));
        when(expiryStateRepository.findCoordinationRowWithLock()).thenReturn(Optional.of(row));
        var entity = buildPendingEntity();
        when(reservationRepository.findExpiredPendingReservations(any()))
            .thenReturn(List.of(entity));
        when(reservationRepository.findByIdWithLock(any()))
            .thenThrow(new RuntimeException("DB error"));

        job.expireReservations();

        verify(expiryStateRepository, atLeastOnce()).save(
            argThat(e -> !e.isProcessingInProgress()));
    }
}
