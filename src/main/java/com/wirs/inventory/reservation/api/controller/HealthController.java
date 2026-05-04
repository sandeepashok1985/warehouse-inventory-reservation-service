package com.wirs.inventory.reservation.api.controller;

import com.wirs.inventory.reservation.application.service.HealthMetrics;
import com.wirs.inventory.reservation.application.service.HealthService;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import com.wirs.inventory.reservation.application.utils.Constants;

/**
 * Public health check endpoint — no authentication required.
 * Delegates to {@link HealthService} which caches database connectivity status
 * with a 30-second TTL via Caffeine to protect against DoS-style polling.
 */
@RestController
public class HealthController {

    private final HealthService healthService;

    public HealthController(HealthService healthService) {
        this.healthService = healthService;
    }

    /**
     * Returns HTTP 200 if the service and database are healthy, HTTP 503 otherwise.
     * The health data is cached by {@link HealthService} — the database is probed
     * at most once per TTL window.
     */
    @GetMapping("/health")
    public ResponseEntity<Map<HealthMetrics, String>> health() {
        Map<HealthMetrics, String> body = healthService.getHealthStatus();
        return Constants.DATABASE_HEALTH_KEY_UP_STRING.equals(body.get(HealthMetrics.STATUS))
            ? ResponseEntity.ok(body)
            : ResponseEntity.status(503).body(body);
    }
}
