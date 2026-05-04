package com.wirs.inventory.reservation.application.service;

/**
 * Metrics keys for health check status responses.
 *
 * <p>Used as keys in the {@code Map<HealthMetrics, String>} returned by {@link HealthService}.
 */
public enum HealthMetrics {
    STATUS,
    DATABASE
}
