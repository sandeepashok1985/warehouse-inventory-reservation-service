package com.wirs.inventory.reservation.api.controller;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.wirs.inventory.reservation.application.service.InventoryService;
import com.wirs.inventory.reservation.domain.exception.SkuNotFoundException;
import com.wirs.inventory.reservation.domain.model.Inventory;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

@Tag("unit")
@WebMvcTest(InventoryController.class)
@AutoConfigureMockMvc(addFilters = false)
class InventoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private InventoryService inventoryService;

    @MockBean
    private MeterRegistry meterRegistry;

    @Test
    void getInventory_returnsStockLevels() throws Exception {
        Inventory inventory = Inventory.builder()
            .sku("A100").totalStock(100).availableStock(80).reservedStock(20).version(1)
            .build();
        when(inventoryService.getInventory("A100")).thenReturn(inventory);

        mockMvc.perform(get("/api/v1/inventory/A100"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.sku").value("A100"))
            .andExpect(jsonPath("$.data.totalStock").value(100));
    }

    @Test
    void getInventory_unknownSku_returns404() throws Exception {
        when(inventoryService.getInventory(anyString()))
            .thenThrow(new SkuNotFoundException("UNKNOWN"));

        mockMvc.perform(get("/api/v1/inventory/UNKNOWN"))
            .andExpect(status().isNotFound());
    }
}
