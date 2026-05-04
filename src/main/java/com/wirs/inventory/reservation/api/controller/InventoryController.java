package com.wirs.inventory.reservation.api.controller;

import com.wirs.inventory.reservation.api.dto.response.ApiResponse;
import com.wirs.inventory.reservation.api.dto.response.InventoryResponse;
import com.wirs.inventory.reservation.application.service.InventoryService;
import com.wirs.inventory.reservation.domain.model.Inventory;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** REST controller for inventory stock level queries. */
@RestController
@RequestMapping("/api/v1/inventory")
public class InventoryController {

    private final InventoryService inventoryService;

    public InventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    /** Returns current stock levels for the given SKU. */
    @GetMapping("/{sku}")
    public ApiResponse<InventoryResponse> getInventory(@PathVariable String sku) {
        Inventory inventory = inventoryService.getInventory(sku);
        return ApiResponse.success(InventoryResponse.from(inventory));  
    }
}
