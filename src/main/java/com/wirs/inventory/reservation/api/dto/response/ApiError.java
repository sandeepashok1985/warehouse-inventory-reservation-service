package com.wirs.inventory.reservation.api.dto.response;

import lombok.Builder;

/** Error descriptor embedded in all API responses when an error occurs. */
@Builder
public record ApiError(String code, String message) {}
