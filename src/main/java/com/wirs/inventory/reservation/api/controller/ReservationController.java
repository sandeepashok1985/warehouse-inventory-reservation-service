package com.wirs.inventory.reservation.api.controller;

import com.wirs.inventory.reservation.api.dto.request.ReserveItemRequest;
import com.wirs.inventory.reservation.api.dto.request.ReserveRequest;
import com.wirs.inventory.reservation.api.dto.response.ApiResponse;
import com.wirs.inventory.reservation.api.dto.response.PagedResponse;
import com.wirs.inventory.reservation.api.dto.response.ReservationItemResponse;
import com.wirs.inventory.reservation.api.dto.response.ReservationResponse;
import com.wirs.inventory.reservation.application.service.ReservationService;
import com.wirs.inventory.reservation.domain.model.Reservation;
import com.wirs.inventory.reservation.domain.model.ReservationItem;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for reservation lifecycle operations.
 *
 * Provides endpoints for creating (POST), confirming, cancelling, and querying
 * reservations. All responses are wrapped in the standard {@link ApiResponse}
 * envelope. Validation is handled by {@code jakarta.validation} annotations on
 * the request DTOs.
 */
@RestController
@RequestMapping("/api/v1/reservations")
public class ReservationController {

    private final ReservationService reservationService;

    public ReservationController(ReservationService reservationService) {
        this.reservationService = reservationService;
    }

    /** Creates a new PENDING reservation. Returns HTTP 201 on success. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ReservationResponse> reserve(
            @RequestBody @NotNull @Valid ReserveRequest request) {

        List<ReservationItem> items = request.items().stream()
            .filter(Objects::nonNull)
            .map(i ->  ReservationItem.builder().sku(i.sku()).quantity(i.quantity()).build())
            .toList();
        
        Reservation reservation = reservationService.reserve(request.orderId(), items);
        return ApiResponse.success(toResponse(reservation));
    }

    /** Transitions a reservation to CONFIRMED. */
    @PostMapping("/{id}/confirm")
    public ApiResponse<ReservationResponse> confirm(@PathVariable UUID id) {
        Reservation reservation = reservationService.confirm(id);
        return ApiResponse.success(toResponse(reservation));
    }

    /** Transitions a reservation to CANCELLED. */
    @PostMapping("/{id}/cancel")
    public ApiResponse<ReservationResponse> cancel(@PathVariable UUID id) {
        Reservation reservation = reservationService.cancel(id);
        return ApiResponse.success(toResponse(reservation));
    }

    /** Returns a reservation by ID. */
    @GetMapping("/{id}")
    public ApiResponse<ReservationResponse> getById(@PathVariable UUID id) {
        Reservation reservation = reservationService.findById(id);
        return ApiResponse.success(toResponse(reservation));
    }

    /** Returns a paginated list of reservations, optionally filtered by status. */
    @GetMapping
    public ApiResponse<PagedResponse<ReservationResponse>> list(
            @RequestParam int page,
            @RequestParam @Min(1) @Max(100) int size,
            @RequestParam(required = false) String status) {
        PageRequest pageable = PageRequest.of(page, size);
        Page<Reservation> pageResult = reservationService.findAll(status, pageable);
        return ApiResponse.success(PagedResponse.from(pageResult.map(this::toResponse)));
    }

    private ReservationResponse toResponse(Reservation reservation) {
        List<ReservationItemResponse> items = reservation.getItems().stream()
            .map(item -> new ReservationItemResponse(item.sku(), item.quantity()))
            .toList();
        return new ReservationResponse(
            reservation.getId(),
            reservation.getOrderId(),
            reservation.status(),
            items,
            reservation.getCreatedAt(),
            reservation.getUpdatedAt(),
            reservation.getExpiresAt()
        );
    }
}
