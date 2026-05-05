package com.wirs.inventory.reservation.application.service;

/**
 * Metrics keys for health check status responses.
 *
 * Used as keys in the {@code Map<HealthMetrics, String>} returned by {@link HealthService}.
 */
public enum HealthMetrics {
    /** Overall service health indicator (UP / DOWN). */
    STATUS,
    /** Database connectivity indicator (UP / DOWN). */
    DATABASE
}
