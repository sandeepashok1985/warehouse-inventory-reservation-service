package com.wirs.inventory.reservation.infrastructure.persistence.repository;

import com.wirs.inventory.reservation.infrastructure.persistence.entity.InventoryEntity;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Spring Data JPA repository for inventory records. */
public interface InventoryJpaRepository extends JpaRepository<InventoryEntity, String> {

    /**
     * Finds inventory for a SKU and acquires a pessimistic write lock.
     * Used in the expiry job's stock release step, not for optimistic-lock allocations.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM InventoryEntity i WHERE i.sku = :sku")
    Optional<InventoryEntity> findBySkuWithLock(@Param("sku") String sku);
}
