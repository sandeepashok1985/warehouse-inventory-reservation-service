package com.wirs.inventory.reservation.api.dto.response;

import lombok.Builder;

/** Universal response envelope. Exactly one of data or error is non-null. */
@Builder
public record ApiResponse<T>(T data, ApiError error) {

    /** Factory: success response with data and null error. */
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(data, null);
    }

    /** Factory: error response with null data and structured error. */
    public static <T> ApiResponse<T> error(String code, String message) {
        return new ApiResponse<>(null, new ApiError(code, message));
    }
}
