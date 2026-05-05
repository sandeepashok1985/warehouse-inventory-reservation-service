package com.wirs.inventory.reservation.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Binds {@code app.reservation.*} configuration properties.
 *
 * @param expiryMinutes the number of minutes after which a PENDING reservation
 *                      expires and becomes eligible for automatic cancellation
 *                      by {@link com.wirs.inventory.reservation.application.job.ReservationExpiryJob}
 */
@ConfigurationProperties(prefix = "app.reservation")
public record ReservationExpiryConfig(int expiryMinutes) {}
