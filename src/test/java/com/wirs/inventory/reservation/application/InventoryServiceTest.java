package com.wirs.inventory.reservation.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wirs.inventory.reservation.application.service.InventoryService;
import com.wirs.inventory.reservation.domain.exception.InsufficientStockException;
import com.wirs.inventory.reservation.domain.exception.InventoryNotInitializedException;
import com.wirs.inventory.reservation.domain.exception.SkuNotFoundException;
import com.wirs.inventory.reservation.domain.model.ReservationItem;
import com.wirs.inventory.reservation.domain.model.SkuAllocationOrder;
import com.wirs.inventory.reservation.infrastructure.persistence.entity.InventoryEntity;
import com.wirs.inventory.reservation.infrastructure.persistence.repository.InventoryJpaRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
class InventoryServiceTest {

    @Mock
    private InventoryJpaRepository inventoryRepository;

    @InjectMocks
    private InventoryService inventoryService;

    private InventoryEntity buildEntity(String sku, long total, long available, long reserved) {
        var entity = new InventoryEntity();
        entity.setSku(sku);
        entity.setTotalStock(total);
        entity.setAvailableStock(available);
        entity.setReservedStock(reserved);
        entity.setVersion(0L);
        return entity;
    }

    @Test
    void allocateStock_sufficientStock_updatesAvailableAndReserved() {
        var entity = buildEntity("A100", 100, 100, 0);
        when(inventoryRepository.findById("A100")).thenReturn(Optional.of(entity));

        inventoryService.allocateStock(
            SkuAllocationOrder.of(List.of(new ReservationItem("A100", 30))));

        var captor = ArgumentCaptor.forClass(InventoryEntity.class);
        verify(inventoryRepository).save(captor.capture());
        assertThat(captor.getValue().getAvailableStock()).isEqualTo(70);
        assertThat(captor.getValue().getReservedStock()).isEqualTo(30);
    }

    @Test
    void allocateStock_insufficientStock_throwsInsufficientStockException() {
        var entity = buildEntity("A100", 100, 20, 80);
        when(inventoryRepository.findById("A100")).thenReturn(Optional.of(entity));

        assertThatThrownBy(() -> inventoryService.allocateStock(
            SkuAllocationOrder.of(List.of(new ReservationItem("A100", 30)))))
            .isInstanceOf(InsufficientStockException.class);
    }

    @Test
    void allocateStock_skuNotFound_throwsInventoryNotInitializedException() {
        when(inventoryRepository.findById("UNKNOWN")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> inventoryService.allocateStock(
            SkuAllocationOrder.of(List.of(new ReservationItem("UNKNOWN", 1)))))
            .isInstanceOf(InventoryNotInitializedException.class);
    }

    @Test
    void allocateStock_multiSku_processesInSkuSortedOrder() {
        var entityA = buildEntity("A100", 100, 100, 0);
        var entityB = buildEntity("B200", 100, 100, 0);
        when(inventoryRepository.findById("A100")).thenReturn(Optional.of(entityA));
        when(inventoryRepository.findById("B200")).thenReturn(Optional.of(entityB));

        inventoryService.allocateStock(
            SkuAllocationOrder.of(List.of(
                new ReservationItem("B200", 1),
                new ReservationItem("A100", 1)
            )));

        var inOrder = inOrder(inventoryRepository);
        inOrder.verify(inventoryRepository).findById("A100");
        inOrder.verify(inventoryRepository).findById("B200");
    }

    @Test
    void releaseStock_releasesReservedStock() {
        var entity = buildEntity("A100", 100, 70, 30);
        when(inventoryRepository.findBySkuWithLock("A100")).thenReturn(Optional.of(entity));

        inventoryService.releaseStock(List.of(new ReservationItem("A100", 30)));

        var captor = ArgumentCaptor.forClass(InventoryEntity.class);
        verify(inventoryRepository).save(captor.capture());
        assertThat(captor.getValue().getAvailableStock()).isEqualTo(100);
        assertThat(captor.getValue().getReservedStock()).isZero();
    }

    @Test
    void getInventory_unknownSku_throwsSkuNotFoundException() {
        when(inventoryRepository.findById("NOSUCHSKU")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> inventoryService.getInventory("NOSUCHSKU"))
            .isInstanceOf(SkuNotFoundException.class);
    }

    @Test
    void getInventory_returnsDomainObject() {
        var entity = buildEntity("A100", 100, 80, 20);
        when(inventoryRepository.findById("A100")).thenReturn(Optional.of(entity));

        var inv = inventoryService.getInventory("A100");

        assertThat(inv.sku()).isEqualTo("A100");
        assertThat(inv.availableStock()).isEqualTo(80);
    }
}
