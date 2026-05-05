package com.wirs.inventory.reservation.integration;

import com.wirs.inventory.reservation.application.job.ReservationExpiryJob;
import com.wirs.inventory.reservation.infrastructure.persistence.repository.ExpiryStateJpaRepository;
import com.wirs.inventory.reservation.infrastructure.persistence.repository.InventoryJpaRepository;
import com.wirs.inventory.reservation.infrastructure.persistence.repository.ReservationEventJpaRepository;
import com.wirs.inventory.reservation.infrastructure.persistence.repository.ReservationJpaRepository;
import jakarta.annotation.PostConstruct;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.DefaultResponseErrorHandler;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Base class for integration tests — starts a real PostgreSQL container via Testcontainers.
 *
 * Requires Docker to be running. Set {@code -DskipDockerTests=true} on the command line
 * to disable all integration tests when Docker is not available.
 *
 * @see <a href="https://www.testcontainers.org/test_framework_integration/junit_5/">Testcontainers JUnit5 docs</a>
 */
@Testcontainers
@DisabledIfSystemProperty(named = "skipDockerTests", matches = "true", disabledReason = "Docker not available (skipDockerTests=true)")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
public abstract class BaseIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
        new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("test_db")
            .withUsername("test_user")
            .withPassword("test_password");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired protected TestRestTemplate restTemplate;

    /**
     * Configures the RestTemplate to treat only 5xx as errors, so 4xx responses
     * are returned normally without throwing exceptions.
     */
    @PostConstruct
    private void configureErrorHandler() {
        restTemplate.getRestTemplate().setErrorHandler(new DefaultResponseErrorHandler() {
            @Override
            protected boolean hasError(HttpStatusCode statusCode) {
                return statusCode.is5xxServerError();
            }
        });
    }
    @Autowired protected ReservationJpaRepository reservationRepository;
    @Autowired protected InventoryJpaRepository inventoryRepository;
    @Autowired protected ReservationEventJpaRepository eventRepository;
    @Autowired protected ExpiryStateJpaRepository expiryStateRepository;
    @Autowired protected ReservationExpiryJob expiryJob;

    protected static final String API_KEY = "dev-key-12345";
    protected static final HttpHeaders AUTH_HEADERS;

    static {
        AUTH_HEADERS = new HttpHeaders();
        AUTH_HEADERS.set("X-API-Key", API_KEY);
    }

    @BeforeEach
    void cleanDatabase() {
        eventRepository.deleteAll();
        reservationRepository.deleteAll();
        inventoryRepository.findAll().forEach(inv -> {
            inv.setAvailableStock(inv.getTotalStock());
            inv.setReservedStock(0L);
            inventoryRepository.save(inv);
        });
        expiryStateRepository.findAll().forEach(state -> {
            state.setProcessingInProgress(false);
            expiryStateRepository.save(state);
        });
    }
}
