package com.wirs.inventory.reservation.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wirs.inventory.reservation.application.event.EventPublisher;
import com.wirs.inventory.reservation.application.service.InventoryService;
import com.wirs.inventory.reservation.application.service.ReservationService;
import com.wirs.inventory.reservation.domain.event.ReservationCancelledEvent;
import com.wirs.inventory.reservation.domain.event.ReservationConfirmedEvent;
import com.wirs.inventory.reservation.domain.event.ReservationCreatedEvent;
import com.wirs.inventory.reservation.domain.exception.DuplicateOrderException;
import com.wirs.inventory.reservation.domain.exception.InvalidStateTransitionException;
import com.wirs.inventory.reservation.domain.exception.ReservationNotFoundException;
import com.wirs.inventory.reservation.domain.factory.ReservationFactory;
import com.wirs.inventory.reservation.domain.model.ReservationItem;
import com.wirs.inventory.reservation.domain.model.SkuAllocationOrder;
import com.wirs.inventory.reservation.infrastructure.persistence.entity.ReservationEntity;
import com.wirs.inventory.reservation.infrastructure.persistence.entity.ReservationItemEntity;
import com.wirs.inventory.reservation.infrastructure.persistence.repository.ReservationJpaRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import com.wirs.inventory.reservation.domain.model.Reservation;
import com.wirs.inventory.reservation.domain.state.PendingState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
class ReservationServiceTest {

    @Mock private ReservationJpaRepository reservationRepository;
    @Mock private InventoryService inventoryService;
    @Mock private ReservationFactory reservationFactory;
    @Mock private EventPublisher eventPublisher;
    @Mock private Clock clock;

    @InjectMocks
    private ReservationService reservationService;

    private final UUID reservationId = UUID.randomUUID();
    private final List<ReservationItem> items = List.of(new ReservationItem("A100", 30));

    private Reservation buildReservation() {
        return Reservation.builder()
            .id(reservationId)
            .orderId("ORD-1")
            .state(new PendingState())
            .items(items)
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(600))
            .build();
    }

    private ReservationEntity buildEntity(String status) {
        return buildEntity(status, items);
    }

    private ReservationEntity buildEntity(String status, List<ReservationItem> domainItems) {
        var entity = new ReservationEntity();
        entity.setId(reservationId);
        entity.setOrderId("ORD-1");
        entity.setStatus(status);
        entity.setCreatedAt(Instant.now());
        entity.setUpdatedAt(Instant.now());
        entity.setExpiresAt(Instant.now().plusSeconds(600));
        var itemEntities = domainItems.stream().map(i -> {
            var ie = new ReservationItemEntity();
            ie.setId(UUID.randomUUID());
            ie.setSku(i.sku());
            ie.setQuantity(i.quantity());
            ie.setReservation(entity);
            return ie;
        }).toList();
        entity.setItems(new ArrayList<>(itemEntities));
        return entity;
    }

    @Test
    void reserve_newOrder_createsReservationAndPublishesEvent() {
        when(reservationFactory.createPendingReservation("ORD-1", items))
            .thenReturn(buildReservation());
        when(reservationRepository.findByOrderId("ORD-1")).thenReturn(Optional.empty());

        var savedEntity = buildEntity("PENDING");
        when(reservationRepository.save(any())).thenReturn(savedEntity);

        reservationService.reserve("ORD-1", items);

        verify(inventoryService).allocateStock(any(SkuAllocationOrder.class));
        verify(eventPublisher).publish(any(ReservationCreatedEvent.class));
    }

    @Test
    void reserve_duplicateOrderId_throwsDuplicateOrderException() {
        when(reservationRepository.findByOrderId("ORD-1"))
            .thenReturn(Optional.of(buildEntity("PENDING")));

        assertThatThrownBy(() -> reservationService.reserve("ORD-1", items))
            .isInstanceOf(DuplicateOrderException.class);
        verify(inventoryService, never()).allocateStock(any());
    }

    @Test
    void confirm_pendingReservation_transitionsToConfirmedAndPublishesEvent() {
        var entity = buildEntity("PENDING");
        when(reservationRepository.findByIdWithLock(reservationId))
            .thenReturn(Optional.of(entity));
        when(reservationRepository.save(any())).thenReturn(entity);
        when(clock.instant()).thenReturn(Instant.now());

        reservationService.confirm(reservationId);

        var captor = ArgumentCaptor.forClass(ReservationEntity.class);
        verify(reservationRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("CONFIRMED");
        verify(eventPublisher).publish(any(ReservationConfirmedEvent.class));
    }

    @Test
    void confirm_nonExistent_throwsReservationNotFoundException() {
        when(reservationRepository.findByIdWithLock(reservationId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> reservationService.confirm(reservationId))
            .isInstanceOf(ReservationNotFoundException.class);
    }

    @Test
    void confirm_alreadyConfirmed_throwsInvalidStateTransitionException() {
        when(reservationRepository.findByIdWithLock(reservationId))
            .thenReturn(Optional.of(buildEntity("CONFIRMED")));

        assertThatThrownBy(() -> reservationService.confirm(reservationId))
            .isInstanceOf(InvalidStateTransitionException.class);
    }

    @Test
    void cancel_pendingReservation_releasesStockAndPublishesEvent() {
        var entity = buildEntity("PENDING");
        when(reservationRepository.findByIdWithLock(reservationId))
            .thenReturn(Optional.of(entity));
        when(reservationRepository.save(any())).thenReturn(entity);
        when(clock.instant()).thenReturn(Instant.now());

        reservationService.cancel(reservationId);

        verify(inventoryService).releaseStock(any());
        var captor = ArgumentCaptor.forClass(ReservationCancelledEvent.class);
        verify(eventPublisher).publish(captor.capture());
        assertThat(captor.getValue().reason()).isEqualTo("API");
    }

    @Test
    void cancel_confirmedReservation_throwsInvalidStateTransitionException() {
        when(reservationRepository.findByIdWithLock(reservationId))
            .thenReturn(Optional.of(buildEntity("CONFIRMED")));

        assertThatThrownBy(() -> reservationService.cancel(reservationId))
            .isInstanceOf(InvalidStateTransitionException.class);
    }

    @Test
    void cancel_nonExistent_throwsReservationNotFoundException() {
        when(reservationRepository.findByIdWithLock(reservationId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> reservationService.cancel(reservationId))
            .isInstanceOf(ReservationNotFoundException.class);
    }

    @Test
    void findById_nonExistent_throwsReservationNotFoundException() {
        when(reservationRepository.findById(reservationId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> reservationService.findById(reservationId))
            .isInstanceOf(ReservationNotFoundException.class);
    }

    @Test
    void reserve_stockAllocationFails_reservationNotPersisted() {
        when(reservationFactory.createPendingReservation("ORD-1", items))
            .thenReturn(buildReservation());
        when(reservationRepository.findByOrderId("ORD-1")).thenReturn(Optional.empty());
        org.mockito.Mockito.doThrow(
            new com.wirs.inventory.reservation.domain.exception.InsufficientStockException(
                "A100", 30, 10))
            .when(inventoryService).allocateStock(any());

        assertThatThrownBy(() -> reservationService.reserve("ORD-1", items))
            .isInstanceOf(com.wirs.inventory.reservation.domain.exception.InsufficientStockException.class);
        verify(reservationRepository, never()).save(any());
    }
}
