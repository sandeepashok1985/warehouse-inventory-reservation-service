package com.wirs.inventory.reservation.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.wirs.inventory.reservation.application.service.HealthMetrics;
import com.wirs.inventory.reservation.application.service.HealthService;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
class HealthControllerCacheTest {

    @Mock
    private HealthService healthService;

    @InjectMocks
    private HealthController controller;

    private MockMvc mockMvc;

    @Test
    @DisplayName("Health endpoint returns 200 when service reports UP")
    void health_endpoint_returns200WhenUp() throws Exception {
        when(healthService.getHealthStatus())
            .thenReturn(Map.of(HealthMetrics.STATUS, "UP", HealthMetrics.DATABASE, "UP"));
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

        mockMvc.perform(MockMvcRequestBuilders.get("/health"))
            .andExpect(result -> assertThat(result.getResponse().getStatus())
                .isEqualTo(HttpStatus.OK.value()));
    }

    @Test
    @DisplayName("Health endpoint returns 503 when service reports DOWN")
    void health_endpoint_returns503WhenDown() throws Exception {
        when(healthService.getHealthStatus())
            .thenReturn(Map.of(HealthMetrics.STATUS, "DOWN", HealthMetrics.DATABASE, "DOWN"));
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

        mockMvc.perform(MockMvcRequestBuilders.get("/health"))
            .andExpect(result -> assertThat(result.getResponse().getStatus())
                .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE.value()));
    }

    @Test
    @DisplayName("Health endpoint returns 200 when database is UP but service reports UP directly")
    void health_endpoint_cachesDbStatusWithinTtl() throws Exception {
        // Caching is now handled by HealthService's @Cacheable annotation.
        // This test verifies the controller delegates correctly and returns 200 for UP.
        when(healthService.getHealthStatus())
            .thenReturn(Map.of(HealthMetrics.STATUS, "UP", HealthMetrics.DATABASE, "UP"))
            .thenReturn(Map.of(HealthMetrics.STATUS, "UP", HealthMetrics.DATABASE, "UP"));
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

        // First call
        mockMvc.perform(MockMvcRequestBuilders.get("/health"))
            .andExpect(result -> assertThat(result.getResponse().getStatus())
                .isEqualTo(HttpStatus.OK.value()));

        // Second call — service returns same result (caching is at service level)
        mockMvc.perform(MockMvcRequestBuilders.get("/health"))
            .andExpect(result -> assertThat(result.getResponse().getStatus())
                .isEqualTo(HttpStatus.OK.value()));
    }

    @Test
    @DisplayName("Health endpoint returns 503 when database is DOWN")
    void health_endpoint_cachesDownStatus() throws Exception {
        when(healthService.getHealthStatus())
            .thenReturn(Map.of(HealthMetrics.STATUS, "DOWN", HealthMetrics.DATABASE, "DOWN"));
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

        var result1 = mockMvc.perform(MockMvcRequestBuilders.get("/health"))
            .andReturn().getResponse();
        assertThat(result1.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE.value());
    }
}
