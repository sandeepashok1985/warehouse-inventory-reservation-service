package com.wirs.inventory.reservation.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Binds app.reservation.* configuration properties for reservation TTL settings. */
@ConfigurationProperties(prefix = "app.reservation")
public record ReservationExpiryConfig(int expiryMinutes) {}
