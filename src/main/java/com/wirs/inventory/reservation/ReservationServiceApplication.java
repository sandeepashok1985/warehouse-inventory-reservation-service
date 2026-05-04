package com.wirs.inventory.reservation;

import com.wirs.inventory.reservation.infrastructure.config.ReservationExpiryConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.retry.annotation.EnableRetry;

/** Entry point for the Warehouse Inventory Reservation Service. */
@SpringBootApplication
@EnableConfigurationProperties(ReservationExpiryConfig.class)
@EnableRetry
public class ReservationServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ReservationServiceApplication.class, args);
    }
}
