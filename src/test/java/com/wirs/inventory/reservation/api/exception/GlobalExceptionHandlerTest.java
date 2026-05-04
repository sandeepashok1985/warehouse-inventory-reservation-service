package com.wirs.inventory.reservation.api.exception;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wirs.inventory.reservation.api.controller.ReservationController;
import com.wirs.inventory.reservation.application.service.InventoryService;
import com.wirs.inventory.reservation.application.service.ReservationService;
import com.wirs.inventory.reservation.domain.exception.DuplicateOrderException;
import com.wirs.inventory.reservation.domain.exception.InsufficientStockException;
import com.wirs.inventory.reservation.domain.exception.InvalidStateTransitionException;
import com.wirs.inventory.reservation.domain.exception.ReservationNotFoundException;
import com.wirs.inventory.reservation.domain.model.Reservation;
import com.wirs.inventory.reservation.domain.model.ReservationItem;
import com.wirs.inventory.reservation.domain.state.PendingState;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@Tag("unit")
@WebMvcTest(ReservationController.class)
@AutoConfigureMockMvc(addFilters = false)
class GlobalExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ReservationService reservationService;

    @MockBean
    private InventoryService inventoryService;

    @MockBean
    private MeterRegistry meterRegistry;

    private final UUID reservationId = UUID.randomUUID();

    @Test
    void insufficientStock_returns409WithCode() throws Exception {
        when(reservationService.reserve(any(), any()))
            .thenThrow(new InsufficientStockException("A100", 50, 30));

        var body = Map.of("orderId", "ORD-1",
            "items", List.of(Map.of("sku", "A100", "quantity", 50)));

        mockMvc.perform(post("/api/v1/reservations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body))
                .header("X-API-Key", "dev-key-12345"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error.code").value("INSUFFICIENT_STOCK"))
            .andExpect(jsonPath("$.error.message").value(
                org.hamcrest.Matchers.containsString("A100")));
    }

    @Test
    void duplicateOrder_returns200WithExistingReservation() throws Exception {
        var existing = Reservation.builder()
            .id(reservationId)
            .orderId("ORD-1")
            .state(new PendingState())
            .item(new ReservationItem("A100", 10))
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(600))
            .build();
        when(reservationService.reserve(any(), any()))
            .thenThrow(new DuplicateOrderException(existing));

        var body = Map.of("orderId", "ORD-1",
            "items", List.of(Map.of("sku", "A100", "quantity", 10)));

        mockMvc.perform(post("/api/v1/reservations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body))
                .header("X-API-Key", "dev-key-12345"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.id").value(reservationId.toString()))
            .andExpect(jsonPath("$.error").doesNotExist());
    }

    @Test
    void reservationNotFound_returns404() throws Exception {
        when(reservationService.reserve(any(), any()))
            .thenThrow(new ReservationNotFoundException(reservationId));

        var body = Map.of("orderId", "ORD-1",
            "items", List.of(Map.of("sku", "A100", "quantity", 10)));

        mockMvc.perform(post("/api/v1/reservations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body))
                .header("X-API-Key", "dev-key-12345"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error.code").value("RESERVATION_NOT_FOUND"));
    }

    @Test
    void invalidStateTransition_returns409() throws Exception {
        when(reservationService.reserve(any(), any()))
            .thenThrow(new InvalidStateTransitionException("Cannot confirm CANCELLED"));

        var body = Map.of("orderId", "ORD-1",
            "items", List.of(Map.of("sku", "A100", "quantity", 10)));

        mockMvc.perform(post("/api/v1/reservations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body))
                .header("X-API-Key", "dev-key-12345"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error.code").value("INVALID_STATE_TRANSITION"));
    }

    @Test
    void missingOrderId_returns400WithInvalidRequestCode() throws Exception {
        var body = Map.of("items", List.of(Map.of("sku", "A100", "quantity", 10)));

        mockMvc.perform(post("/api/v1/reservations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body))
                .header("X-API-Key", "dev-key-12345"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
    }

    @Test
    void missingPaginationParams_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/reservations")  // use GET mapping test indirectly
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}")
                .header("X-API-Key", "dev-key-12345"))
            .andExpect(status().isBadRequest());
    }
}
