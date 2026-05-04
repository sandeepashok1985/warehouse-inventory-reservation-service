package com.wirs.inventory.reservation.api.exception;

import com.wirs.inventory.reservation.api.dto.response.ApiResponse;
import com.wirs.inventory.reservation.api.dto.response.ReservationItemResponse;
import com.wirs.inventory.reservation.api.dto.response.ReservationResponse;
import com.wirs.inventory.reservation.domain.exception.DuplicateOrderException;
import com.wirs.inventory.reservation.domain.exception.InventoryNotInitializedException;
import com.wirs.inventory.reservation.domain.exception.InvalidStateTransitionException;
import com.wirs.inventory.reservation.domain.exception.InsufficientStockException;
import com.wirs.inventory.reservation.domain.exception.ReservationNotFoundException;
import com.wirs.inventory.reservation.domain.exception.SkuNotFoundException;
import com.wirs.inventory.reservation.domain.model.Reservation;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/** Centralised HTTP error mapping for all domain and validation exceptions. */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final MeterRegistry meterRegistry;

    public GlobalExceptionHandler(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    /** Maps InsufficientStockException → 409 CONFLICT. */
    @ExceptionHandler(InsufficientStockException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ApiResponse<Void> handleInsufficientStock(InsufficientStockException ex) {
        return ApiResponse.error("INSUFFICIENT_STOCK", ex.getMessage());
    }

    /** Maps ReservationNotFoundException → 404 NOT FOUND. */
    @ExceptionHandler(ReservationNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiResponse<Void> handleNotFound(ReservationNotFoundException ex) {
        return ApiResponse.error("RESERVATION_NOT_FOUND", ex.getMessage());
    }

    /** Maps SkuNotFoundException → 404 NOT FOUND. */
    @ExceptionHandler(SkuNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiResponse<Void> handleSkuNotFound(SkuNotFoundException ex) {
        return ApiResponse.error("SKU_NOT_FOUND", ex.getMessage());
    }

    /**
     * Maps InventoryNotInitializedException → 500 INTERNAL_SERVER_ERROR.
     * Increments a Micrometer counter so the alert fires immediately.
     */
    @ExceptionHandler(InventoryNotInitializedException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiResponse<Void> handleInventoryNotInitialized(InventoryNotInitializedException ex) {
        meterRegistry.counter("inventory.not_initialized.count").increment();
        log.error("CRITICAL — inventory not initialized: {}", ex.getMessage());
        return ApiResponse.error("INVENTORY_NOT_INITIALIZED", ex.getMessage());
    }

    /** Maps InvalidStateTransitionException → 409 CONFLICT. */
    @ExceptionHandler(InvalidStateTransitionException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ApiResponse<Void> handleInvalidTransition(InvalidStateTransitionException ex) {
        return ApiResponse.error("INVALID_STATE_TRANSITION", ex.getMessage());
    }

    /**
     * Maps DuplicateOrderException → HTTP 200.
     * Returns the existing reservation — this is a success path, not an error.
     */
    @ExceptionHandler(DuplicateOrderException.class)
    @ResponseStatus(HttpStatus.OK)
    public ApiResponse<ReservationResponse> handleDuplicateOrder(DuplicateOrderException ex) {
        return ApiResponse.success(toReservationResponse(ex));
    }

    /** Maps @Valid constraint violations → 400 BAD REQUEST. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleValidationErrors(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
            .map(e -> e.getField() + ": " + e.getDefaultMessage())
            .collect(Collectors.joining("; "));
        return ApiResponse.error("INVALID_REQUEST", message);
    }

    /** Maps bad enum value in request parameter → 400. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return ApiResponse.error("INVALID_REQUEST",
            "Invalid value for parameter '" + ex.getName() + "': " + ex.getValue());
    }

    /** Maps missing required parameters → 400. */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleMissingParam(MissingServletRequestParameterException ex) {
        return ApiResponse.error("INVALID_REQUEST",
            "Required parameter '" + ex.getParameterName() + "' is missing");
    }

    /** Maps @Valid constraint violations on records → 400 BAD_REQUEST. */
    @ExceptionHandler(HandlerMethodValidationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleHandlerMethodValidation(HandlerMethodValidationException ex) {
        String message = ex.getAllValidationResults().stream()
            .flatMap(r -> r.getResolvableErrors().stream())
            .map(e -> e.getDefaultMessage())
            .collect(Collectors.joining("; "));
        return ApiResponse.error("INVALID_REQUEST", message);
    }

    private ReservationResponse toReservationResponse(DuplicateOrderException ex) {
        Reservation reservation = ex.getExistingReservation();
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
