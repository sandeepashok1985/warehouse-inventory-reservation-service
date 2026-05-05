package com.wirs.inventory.reservation.infrastructure.persistence.entity;

import com.wirs.inventory.reservation.domain.exception.InsufficientStockException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** JPA entity mapping the inventory table. Carries a JPA optimistic lock version field. */
@Entity
@Table(name = "inventory")
@Getter
@Setter
@NoArgsConstructor
public class InventoryEntity {

    @Id
    @Column(name = "sku")
    private String sku;

    @Column(name = "total_stock", nullable = false)
    private long totalStock;

    @Column(name = "available_stock", nullable = false)
    private long availableStock;

    @Column(name = "reserved_stock", nullable = false)
    private long reservedStock;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    /**
     * Allocates stock by moving the given quantity from available to reserved.
     *
     * @param qty the quantity to allocate
     * @throws InsufficientStockException if available stock is less than qty
     */
    public void allocateReserved(long qty) {
        // Defensive check: caller must verify stock availability before calling.
        // This throws if a concurrent optimistic-lock retry loop exhausted attempts.
        if (availableStock < qty) {
            throw new InsufficientStockException(sku, qty, availableStock);
        }
        this.availableStock -= qty;
        this.reservedStock += qty;
    }

    /**
     * Releases a quantity of reserved stock back to available.
     *
     * @param qty the quantity to release
     * @throws IllegalStateException if reserved stock is less than qty
     */
    public void releaseReserved(long qty) {
        // Defensive check: reserved stock should always be >= release quantity.
        // If this fails, it indicates a logic error (double-release or mismatched item).
        if (reservedStock < qty) {
            throw new IllegalStateException(
                "Cannot release %d units for SKU %s — only %d are reserved".formatted(qty, sku, reservedStock));
        }
        this.availableStock += qty;
        this.reservedStock -= qty;
    }
}
