package com.wirs.inventory.reservation.application.utils;

/** Shared constants used across health-check and caching configuration. */
public final class Constants {

    public static final String HEALTH_CACHE_NAME = "health";
    public static final String DATABASE_HEALTH_KEY_UP_STRING = "UP";
    public static final String DATABASE_HEALTH_KEY_DOWN_STRING = "DOWN";

    private Constants() {
        // prevent instantiation
    }
}
