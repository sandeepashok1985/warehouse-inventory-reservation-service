package com.wirs.inventory.reservation.application.utils;

/**
 * Shared constants used across health-check and caching configuration.
 *
 * Centralises string literals that are referenced from multiple layers
 * (configuration, controllers, services) to avoid duplication and typos.
 */
public final class Constants {

    /** Caffeine cache name for health-check responses (30-second TTL). */
    public static final String HEALTH_CACHE_NAME = "health";

    /** Health status indicator for a healthy component. */
    public static final String DATABASE_HEALTH_KEY_UP_STRING = "UP";

    /** Health status indicator for an unhealthy component. */
    public static final String DATABASE_HEALTH_KEY_DOWN_STRING = "DOWN";

    private Constants() {
        // prevent instantiation
    }
}
