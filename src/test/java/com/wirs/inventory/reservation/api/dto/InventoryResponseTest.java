package com.wirs.inventory.reservation.api.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.wirs.inventory.reservation.api.dto.response.InventoryResponse;
import com.wirs.inventory.reservation.domain.model.Inventory;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
class InventoryResponseTest {

    @Test
    void from_mapsAllFields() {
        Inventory inventory = Inventory.builder()
            .sku("B200").totalStock(500).availableStock(300).reservedStock(200).version(3)
            .build();

        InventoryResponse response = InventoryResponse.from(inventory);

        assertThat(response.sku()).isEqualTo("B200");
        assertThat(response.totalStock()).isEqualTo(500);
        assertThat(response.availableStock()).isEqualTo(300);
        assertThat(response.reservedStock()).isEqualTo(200);
    }

    @Test
    void builder_createsResponseDirectly() {
        InventoryResponse response = InventoryResponse.builder()
            .sku("C300").totalStock(10).availableStock(5).reservedStock(5)
            .build();

        assertThat(response.sku()).isEqualTo("C300");
    }
}
