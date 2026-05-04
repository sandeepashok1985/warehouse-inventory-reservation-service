package com.wirs.inventory.reservation.infrastructure.config;

import com.wirs.inventory.reservation.domain.factory.ReservationFactory;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Provides the system clock as a Spring bean for injection and test overriding. */
@Configuration
public class ClockConfig {

    /** Provides the UTC system clock for production use. Tests override via @TestConfiguration. */
    @Bean
    public Clock utcClock() {
        return Clock.systemUTC();
    }

    /**
     * Creates the reservation factory with the configured TTL and system clock.
     * The factory is a domain-layer class with zero Spring dependencies.
     */
    @Bean
    public ReservationFactory reservationFactory(Clock utcClock,
                                                  ReservationExpiryConfig expiryConfig) {
        return new ReservationFactory(utcClock, expiryConfig.expiryMinutes());
    }
}
