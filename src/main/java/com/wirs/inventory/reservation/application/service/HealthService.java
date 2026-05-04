package com.wirs.inventory.reservation.application.service;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;
import javax.sql.DataSource;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import com.wirs.inventory.reservation.application.utils.Constants;

/**
 * Application service for health check status.
 * Database connectivity is cached via {@code @Cacheable} with a 30-second TTL
 * to protect against DoS-style polling that would otherwise consume
 * connection-pool resources on every request.
 */
@Service
public class HealthService {

    private final DataSource dataSource;

    public HealthService(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /**
     * Returns the current health status, cached for 30 seconds.
     * On cache miss, probes database connectivity with {@code SELECT 1}.
     *
     * @return map with {@link HealthMetrics} keys — {@code STATUS} (UP/DOWN)
     *     and {@code DATABASE} (UP/DOWN)
     */
    @Cacheable(Constants.HEALTH_CACHE_NAME)
    public Map<HealthMetrics, String> getHealthStatus() {
        boolean dbWorking = dbConnectionWorking();
        String status = dbWorking ? Constants.DATABASE_HEALTH_KEY_UP_STRING : Constants.DATABASE_HEALTH_KEY_DOWN_STRING;
        return Map.of(HealthMetrics.STATUS, status, HealthMetrics.DATABASE, dbWorking ? Constants.DATABASE_HEALTH_KEY_UP_STRING : Constants.DATABASE_HEALTH_KEY_DOWN_STRING);
    }

    private boolean dbConnectionWorking() {
        try (Connection connection = dataSource.getConnection();
              Statement stmt = connection.createStatement()) {
              stmt.execute("SELECT 1");
            return true;
        } catch (SQLException e) {
            return false;
        }
    }
}
