package com.wirs.inventory.reservation.application.service;

import com.wirs.inventory.reservation.domain.exception.InventoryNotInitializedException;
import com.wirs.inventory.reservation.domain.exception.SkuNotFoundException;
import com.wirs.inventory.reservation.domain.model.Inventory;
import com.wirs.inventory.reservation.domain.model.ReservationItem;
import com.wirs.inventory.reservation.domain.model.SkuAllocationOrder;
import com.wirs.inventory.reservation.infrastructure.persistence.entity.InventoryEntity;
import com.wirs.inventory.reservation.infrastructure.persistence.repository.InventoryJpaRepository;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Application service for inventory stock management operations.
 *
 * Concurrency strategy is split by operation type. allocateStock uses optimistic
 * locking via @Version on InventoryEntity combined with @Retryable; releaseStock
 * uses pessimistic locking (SELECT ... FOR UPDATE) because the expiry and cancel
 * paths cannot tolerate retry overhead. Batch updates are not configured -- each
 * save() issues an individual UPDATE at flush time.
 *
 * All public methods are @Transactional, with read-only methods marked explicitly
 * to allow Hibernate to skip dirty checks.
 */
@Service
@Transactional
public class InventoryService {

    private static final Logger log = LoggerFactory.getLogger(InventoryService.class);

    private final InventoryJpaRepository inventoryRepository;

    public InventoryService(InventoryJpaRepository inventoryRepository) {
        this.inventoryRepository = inventoryRepository;
    }

    /**
     * Allocates stock for all items in one transactional operation, in SKU-sorted
     * order. The order is guaranteed sorted by SkuAllocationOrder.of().
     *
     * Optimistic locking works via @Version on InventoryEntity. If a concurrent
     * transaction updates the same row first, the WHERE version=? clause matches
     * zero rows and Hibernate throws ObjectOptimisticLockingFailureException.
     * @Retryable catches this and re-runs the entire method up to 3 times with
     * exponential backoff and jitter, ensuring forward progress without holding
     * database row locks.
     *
     * Each item is processed sequentially within the loop. The entity is loaded
     * via findById, mutated through allocateReserved, and saved. The UPDATE is
     * deferred to transaction commit time.
     *
     * @param order pre-sorted allocation order produced by SkuAllocationOrder.of().
     * @throws InsufficientStockException if any SKU lacks available stock after
     *     all retry attempts are exhausted.
     * @throws InventoryNotInitializedException if any SKU has no inventory record
     *     (data integrity failure -- the product exists but no row was seeded).
     */
    @Retryable(
        retryFor = ObjectOptimisticLockingFailureException.class,
        maxAttempts = 3,
        backoff = @Backoff(delay = 100, multiplier = 2, maxDelay = 1000, random = true)
    )
    public void allocateStock(SkuAllocationOrder order) {
        for (ReservationItem item : order.items()) {
            InventoryEntity entity = inventoryRepository.findById(item.sku())
                .orElseThrow(() -> new InventoryNotInitializedException(item.sku()));
            entity.allocateReserved(item.quantity());
            inventoryRepository.save(entity);
            log.debug("Stock allocated: sku={}, qty={}", item.sku(), item.quantity());
        }
    }

    /**
     * Releases reserved stock back to available. Called on reservation cancel
     * or expiry.
     *
     * Uses pessimistic locking (SELECT ... FOR UPDATE) via
     * InventoryJpaRepository.findBySkuWithLock to serialise concurrent releases
     * for the same SKU at the database level. This is intentional: the expiry job
     * processes many reservations in batch, and optimistic retries would add
     * unacceptable latency. A blocked transaction simply waits for the lock
     * holder to commit.
     *
     * The lock is acquired on the SELECT and held until the enclosing
     * @Transactional method commits, at which point the UPDATE is flushed and
     * the row lock is released.
     *
     * @param items the items whose reserved stock should be released. Each item
     *     is processed sequentially; items for the same SKU are locked one at a
     *     time.
     * @throws InventoryNotInitializedException if the SKU record has disappeared
     *     (data integrity failure -- should never happen if cancellations are
     *     always preceded by a successful allocation).
     */
    public void releaseStock(List<ReservationItem> items) {
        for (ReservationItem item : items) {
            InventoryEntity entity = inventoryRepository.findBySkuWithLock(item.sku())
                .orElseThrow(() -> new InventoryNotInitializedException(item.sku()));
            entity.releaseReserved(item.quantity());
            inventoryRepository.save(entity);
            log.debug("Stock released: sku={}, qty={}", item.sku(), item.quantity());
        }
    }

    /**
     * Returns a snapshot of current stock levels for a SKU.
     *
     * This is a read-only operation. The @Transactional(readOnly = true) hint
     * allows Hibernate to skip dirty checking and flush cycles, reducing overhead.
     * No database locks are acquired during this read.
     *
     * The returned Inventory is constructed from the entity;
     * it is detached from the persistence context and safe to use outside the
     * transaction.
     *
     * @param sku the SKU to look up.
     * @return the current Inventory state (total, available, reserved, version).
     * @throws SkuNotFoundException if the SKU does not exist in the inventory
     *     table (404 -- valid client lookup miss).
     */
    @Transactional(readOnly = true)
    public Inventory getInventory(String sku) {
        InventoryEntity entity = inventoryRepository.findById(sku)
            .orElseThrow(() -> new SkuNotFoundException(sku));
        return toDomain(entity);
    }

    private Inventory toDomain(InventoryEntity entity) {
        return Inventory.builder()
            .sku(entity.getSku())
            .totalStock(entity.getTotalStock())
            .availableStock(entity.getAvailableStock())
            .reservedStock(entity.getReservedStock())
            .version(entity.getVersion())
            .build();
    }
}
