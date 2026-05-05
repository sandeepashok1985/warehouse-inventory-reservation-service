package com.wirs.inventory.reservation.application.service;

import com.wirs.inventory.reservation.application.event.EventPublisher;
import com.wirs.inventory.reservation.domain.event.ReservationCancelledEvent;
import com.wirs.inventory.reservation.domain.event.ReservationConfirmedEvent;
import com.wirs.inventory.reservation.domain.event.ReservationCreatedEvent;
import com.wirs.inventory.reservation.domain.exception.DuplicateOrderException;
import com.wirs.inventory.reservation.domain.exception.InsufficientStockException;
import com.wirs.inventory.reservation.domain.exception.InvalidStateTransitionException;
import com.wirs.inventory.reservation.domain.exception.ReservationNotFoundException;
import com.wirs.inventory.reservation.domain.factory.ReservationFactory;
import com.wirs.inventory.reservation.domain.model.Reservation;
import com.wirs.inventory.reservation.domain.model.ReservationItem;
import com.wirs.inventory.reservation.domain.model.SkuAllocationOrder;
import com.wirs.inventory.reservation.infrastructure.persistence.entity.ReservationEntity;
import com.wirs.inventory.reservation.infrastructure.persistence.mapper.ReservationMapper;
import com.wirs.inventory.reservation.infrastructure.persistence.repository.ReservationJpaRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Application service for reservation lifecycle management.
 *
 * Coordinates the full reservation workflow: creation with stock allocation,
 * confirmation, cancellation with stock release, and read queries. Relies on
 * {@link InventoryService} for stock mutations and {@link ReservationFactory}
 * for domain aggregate construction. Domain events are published after every
 * successful state change via {@link EventPublisher}.
 *
 * Uses pessimistic locking ({@code SELECT ... FOR UPDATE}) for confirm/cancel
 * to safely serialise concurrent operations on the same reservation row, while
 * the initial creation path relies on the {@code order_id} unique constraint for
 * idempotency.
 *
 * @see InventoryService
 * @see ReservationFactory
 * @see EventPublisher
 */
@Service
@Transactional
public class ReservationService {

    private static final Logger log = LoggerFactory.getLogger(ReservationService.class);

    private final ReservationJpaRepository reservationRepository;
    private final InventoryService inventoryService;
    private final ReservationFactory reservationFactory;
    private final EventPublisher eventPublisher;
    private final Clock clock;

    public ReservationService(ReservationJpaRepository reservationRepository,
                               InventoryService inventoryService,
                               ReservationFactory reservationFactory,
                               EventPublisher eventPublisher,
                               Clock clock) {
        this.reservationRepository = reservationRepository;
        this.inventoryService = inventoryService;
        this.reservationFactory = reservationFactory;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    /**
     * Creates a new PENDING reservation and allocates stock.
     * Returns the existing reservation if orderId already exists (idempotent).
     *
     * @param orderId the external order reference (must be unique)
     * @param items   the line items to reserve
     * @return the newly created (or existing) reservation
     * @throws InsufficientStockException
     *     if any SKU lacks available stock after all retry attempts.
     * @throws DuplicateOrderException if orderId already exists (mapped to HTTP 200).
     */
    public Reservation reserve(String orderId, List<ReservationItem> items) {
        // Fast-path check: if orderId already exists, return the existing reservation
        // as an idempotent success (HTTP 200) rather than failing.
        Optional<ReservationEntity> existing = reservationRepository.findByOrderId(orderId);
        if (existing.isPresent()) {
            throw new DuplicateOrderException(ReservationMapper.toDomain(existing.get()));
        }

        Reservation reservation = reservationFactory.createPendingReservation(orderId, items);
        inventoryService.allocateStock(SkuAllocationOrder.of(items));

        try {
            ReservationEntity savedEntity = reservationRepository.save(ReservationMapper.toEntity(reservation));
            Reservation savedReservation = ReservationMapper.toDomain(savedEntity);
            log.info("Reservation created: id={}, orderId={}, items={}, expiresAt={}",
                savedReservation.getId(), savedReservation.getOrderId(),
                savedReservation.getItems().size(), savedReservation.getExpiresAt());
            eventPublisher.publish(ReservationCreatedEvent.fromReservation(savedReservation, Instant.now(clock)));
            return savedReservation;
        } catch (DataIntegrityViolationException e) {
            // Race condition recovery: two concurrent requests for the same orderId
            // both passed the fast-path check above. The second INSERT hits the
            // unique constraint. We query the winner and return the existing reservation.
            ReservationEntity winner = reservationRepository.findByOrderId(orderId)
                .orElseThrow(() -> new IllegalStateException(
                    "Unique constraint violated but reservation not found: " + orderId));
            log.warn("Duplicate reservation attempt: orderId={}", orderId);
            throw new DuplicateOrderException(ReservationMapper.toDomain(winner));
        }
    }

    /**
     * Transitions a PENDING reservation to CONFIRMED.
     *
     * @param reservationId the UUID of the reservation to confirm
     * @return the confirmed reservation
     * @throws ReservationNotFoundException if the reservation does not exist.
     * @throws InvalidStateTransitionException
     *     if the reservation is not PENDING.
     */
    public Reservation confirm(UUID reservationId) {
        ReservationEntity entity = reservationRepository.findByIdWithLock(reservationId)
            .orElseThrow(() -> new ReservationNotFoundException(reservationId));
        Reservation reservation = ReservationMapper.toDomain(entity);

        // Validates the state transition and updates the reservation state to CONFIRMED
        reservation.confirm();
        reservation.setUpdatedAt(Instant.now(clock));

        // Updates the entity status and saves it to the database
        entity.updateStatus(reservation.status(), Instant.now(clock));
        reservationRepository.save(entity);

        log.info("Reservation confirmed: id={}, orderId={}",
            reservation.getId(), reservation.getOrderId());

        // Publishes the ReservationConfirmedEvent after successful confirmation
        eventPublisher.publish(ReservationConfirmedEvent.fromReservation(reservation, Instant.now(clock)));
        return reservation;
    }

    /**
     * Transitions a PENDING reservation to CANCELLED and releases held stock.
     *
     * @param reservationId the UUID of the reservation to cancel
     * @return the cancelled reservation
     * @throws ReservationNotFoundException if the reservation does not exist.
     * @throws com.wirs.inventory.reservation.domain.exception.InvalidStateTransitionException
     *     if the reservation is not PENDING.
     */
    public Reservation cancel(UUID reservationId) {
        ReservationEntity entity = reservationRepository.findByIdWithLock(reservationId)
            .orElseThrow(() -> new ReservationNotFoundException(reservationId));
        Reservation reservation = ReservationMapper.toDomain(entity);

        // Validates the state transition and updates the reservation state to CANCELLED
        reservation.cancel();
        reservation.setUpdatedAt(Instant.now(clock));

        // Releases the allocated stock back to inventory
        inventoryService.releaseStock(reservation.getItems());

        // Updates the entity status and saves it to the database
        entity.updateStatus(reservation.status(), Instant.now(clock));
        reservationRepository.save(entity);

        log.info("Reservation cancelled: id={}, orderId={}, items={}",
            reservation.getId(), reservation.getOrderId(), reservation.getItems().size());

        // Publishes the ReservationCancelledEvent after successful cancellation
        eventPublisher.publish(ReservationCancelledEvent.from(reservation, "API", Instant.now(clock)));
        return reservation;
    }

    /** Returns a reservation by ID. */
    @Transactional(readOnly = true)
    public Reservation findById(UUID reservationId) {
        return reservationRepository.findById(reservationId)
            .map(ReservationMapper::toDomain)
            .orElseThrow(() -> new ReservationNotFoundException(reservationId));
    }

    /** Returns a paginated list of reservations, optionally filtered by status. */
    @Transactional(readOnly = true)
    public Page<Reservation> findAll(@Nullable String status, Pageable pageable) {
        if (status != null) {
            return reservationRepository.findByStatus(status, pageable)
                .map(ReservationMapper::toDomain);
        }
        return reservationRepository.findAll(pageable).map(ReservationMapper::toDomain);
    }


}
