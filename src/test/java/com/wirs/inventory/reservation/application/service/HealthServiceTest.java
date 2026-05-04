package com.wirs.inventory.reservation.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.wirs.inventory.reservation.application.utils.Constants;
import java.sql.Connection;
import java.sql.Statement;
import javax.sql.DataSource;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
class HealthServiceTest {

    @Mock
    private DataSource dataSource;

    @Test
    void getHealthStatus_returnsUpWhenDbWorks() throws Exception {
        Connection connection = Mockito.mock(Connection.class);
        Statement statement = Mockito.mock(Statement.class);
        Mockito.when(dataSource.getConnection()).thenReturn(connection);
        Mockito.when(connection.createStatement()).thenReturn(statement);
        Mockito.when(statement.execute("SELECT 1")).thenReturn(true);

        HealthService healthService = new HealthService(dataSource);

        var status = healthService.getHealthStatus();
        assertThat(status.get(HealthMetrics.STATUS)).isEqualTo("UP");
        assertThat(status.get(HealthMetrics.DATABASE)).isEqualTo("UP");
    }

    @Test
    void getHealthStatus_returnsDownWhenDbFails() throws Exception {
        Mockito.when(dataSource.getConnection()).thenThrow(new java.sql.SQLException("DB down"));

        HealthService healthService = new HealthService(dataSource);

        var status = healthService.getHealthStatus();
        assertThat(status.get(HealthMetrics.STATUS)).isEqualTo("DOWN");
        assertThat(status.get(HealthMetrics.DATABASE)).isEqualTo("DOWN");
    }
}
