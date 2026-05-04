package com.wirs.inventory.reservation.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

/**
 * Full lifecycle integration tests using H2 in-memory database.
 * Verifies the complete reservation flow: create → confirm → cancel,
 * plus idempotency, validation, and outbox event persistence.
 */
@Tag("integration")
class ReservationLifecycleH2IntegrationTest extends BaseIntegrationTest {

    private HttpEntity<Map<String, Object>> buildReserveRequest(String orderId, String sku, long qty) {
        var body = Map.<String, Object>of(
            "orderId", orderId,
            "items", List.of(Map.of("sku", sku, "quantity", qty))
        );
        return new HttpEntity<>(body, AUTH_HEADERS);
    }

    @Test
    void createReservation_returns201WithPendingStatus() {
        var response = restTemplate.exchange(
            "/api/v1/reservations", HttpMethod.POST,
            buildReserveRequest("ORD-H2-001", "A100", 10),
            JsonNode.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().get("data").get("status").asText()).isEqualTo("PENDING");

        var inventory = inventoryRepository.findById("A100").orElseThrow();
        assertThat(inventory.getAvailableStock()).isEqualTo(990L);
    }

    @Test
    void createAndConfirmReservation_fullLifecycle() {
        var createResp = restTemplate.exchange(
            "/api/v1/reservations", HttpMethod.POST,
            buildReserveRequest("ORD-H2-002", "A100", 10),
            JsonNode.class);
        assertThat(createResp.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        var id = createResp.getBody().get("data").get("id").asText();

        var confirmResp = restTemplate.exchange(
            "/api/v1/reservations/" + id + "/confirm", HttpMethod.POST,
            new HttpEntity<>(AUTH_HEADERS), JsonNode.class);
        assertThat(confirmResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(confirmResp.getBody().get("data").get("status").asText()).isEqualTo("CONFIRMED");

        var getResp = restTemplate.exchange(
            "/api/v1/reservations/" + id, HttpMethod.GET,
            new HttpEntity<>(AUTH_HEADERS), JsonNode.class);
        assertThat(getResp.getBody().get("data").get("status").asText()).isEqualTo("CONFIRMED");

        var events = eventRepository.findAll();
        assertThat(events).isNotEmpty();
        assertThat(events).extracting("eventType").contains("CREATED", "CONFIRMED");
    }

    @Test
    void createAndCancelReservation_stockRestored() {
        var createResp = restTemplate.exchange(
            "/api/v1/reservations", HttpMethod.POST,
            buildReserveRequest("ORD-H2-003", "A100", 30),
            JsonNode.class);
        var id = createResp.getBody().get("data").get("id").asText();

        restTemplate.exchange(
            "/api/v1/reservations/" + id + "/cancel", HttpMethod.POST,
            new HttpEntity<>(AUTH_HEADERS), JsonNode.class);

        var inventoryResp = restTemplate.exchange(
            "/api/v1/inventory/A100", HttpMethod.GET,
            new HttpEntity<>(AUTH_HEADERS), JsonNode.class);
        assertThat(inventoryResp.getBody().get("data").get("availableStock").asLong())
            .isEqualTo(1000L);
    }

    @Test
    void confirmAlreadyConfirmed_returns409WithInvalidStateTransition() {
        var createResp = restTemplate.exchange(
            "/api/v1/reservations", HttpMethod.POST,
            buildReserveRequest("ORD-H2-004", "A100", 5),
            JsonNode.class);
        var id = createResp.getBody().get("data").get("id").asText();

        restTemplate.exchange("/api/v1/reservations/" + id + "/confirm",
            HttpMethod.POST, new HttpEntity<>(AUTH_HEADERS), JsonNode.class);

        var second = restTemplate.exchange("/api/v1/reservations/" + id + "/confirm",
            HttpMethod.POST, new HttpEntity<>(AUTH_HEADERS), JsonNode.class);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(second.getBody().get("error").get("code").asText())
            .isEqualTo("INVALID_STATE_TRANSITION");
    }

    @Test
    void duplicateOrderId_returns200WithExistingReservation() {
        var first = restTemplate.exchange(
            "/api/v1/reservations", HttpMethod.POST,
            buildReserveRequest("ORD-H2-DUP", "A100", 5),
            JsonNode.class);
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        var firstId = first.getBody().get("data").get("id").asText();

        var second = restTemplate.exchange(
            "/api/v1/reservations", HttpMethod.POST,
            buildReserveRequest("ORD-H2-DUP", "A100", 5),
            JsonNode.class);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(second.getBody().get("data").get("id").asText()).isEqualTo(firstId);
    }

    @Test
    void getReservation_unknownId_returns404() {
        var response = restTemplate.exchange(
            "/api/v1/reservations/" + UUID.randomUUID(), HttpMethod.GET,
            new HttpEntity<>(AUTH_HEADERS), JsonNode.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void reserveWithInsufficientStock_returns409() {
        var response = restTemplate.exchange(
            "/api/v1/reservations", HttpMethod.POST,
            buildReserveRequest("ORD-H2-OVER", "A100", 9999),
            JsonNode.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().get("error").get("code").asText())
            .isEqualTo("INSUFFICIENT_STOCK");
    }

    @Test
    void security_missingApiKey_returns401() {
        var body = Map.<String, Object>of(
            "orderId", "ORD-H2-AUTH",
            "items", List.of(Map.of("sku", "A100", "quantity", 1))
        );
        // Use Apache HttpClient to avoid JDK HttpRetryException on 401 responses
        var savedFactory = restTemplate.getRestTemplate().getRequestFactory();
        try {
            java.net.http.HttpClient jdkClient = java.net.http.HttpClient.newBuilder()
                .version(java.net.http.HttpClient.Version.HTTP_1_1)
                .build();
            var factory = new org.springframework.http.client.JdkClientHttpRequestFactory(jdkClient);
            restTemplate.getRestTemplate().setRequestFactory(factory);
            var response = restTemplate.exchange(
                "/api/v1/reservations", HttpMethod.POST,
                new HttpEntity<>(body), JsonNode.class);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        } finally {
            restTemplate.getRestTemplate().setRequestFactory(savedFactory);
        }
    }

    @Test
    void healthEndpoint_publicAccess_returns200() {
        var response = restTemplate.exchange(
            "/health", HttpMethod.GET,
            HttpEntity.EMPTY, JsonNode.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void expiryJob_cancelsExpiredReservation_andReleasesStock() {
        var inventory = inventoryRepository.findById("B200").orElseThrow();
        long initialAvailable = inventory.getAvailableStock();

        var response = restTemplate.exchange(
            "/api/v1/reservations", HttpMethod.POST,
            buildReserveRequest("ORD-H2-EXP", "B200", 20),
            JsonNode.class);
        var id = UUID.fromString(response.getBody().get("data").get("id").asText());

        // Force the reservation to be expired by directly updating expires_at
        var res = reservationRepository.findById(id).orElseThrow();
        res.setExpiresAt(Instant.now().minusSeconds(60));
        reservationRepository.save(res);

        // Run expiry job directly
        expiryJob.processExpiredReservations();

        var expired = reservationRepository.findById(id).orElseThrow();
        assertThat(expired.getStatus()).isEqualTo("CANCELLED");

        var invAfter = inventoryRepository.findById("B200").orElseThrow();
        assertThat(invAfter.getAvailableStock()).isEqualTo(initialAvailable);
    }
}
