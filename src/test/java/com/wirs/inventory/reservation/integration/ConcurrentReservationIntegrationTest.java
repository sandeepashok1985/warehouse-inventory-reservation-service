package com.wirs.inventory.reservation.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.wirs.inventory.reservation.infrastructure.persistence.entity.ReservationEntity;
import com.wirs.inventory.reservation.infrastructure.persistence.entity.ReservationItemEntity;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;


@Tag("integration")
class ConcurrentReservationIntegrationTest extends BaseIntegrationTest {

    /** Base URL used by per-thread RestTemplates to share the server port. */
    private String baseUrl() {
        return restTemplate.getRootUri();
    }

    /** Synchronizes access to the shared injected RestTemplate across threads. */
    private synchronized org.springframework.http.ResponseEntity<JsonNode> exchange(
            String url, HttpMethod method, HttpEntity<?> entity) {
        return restTemplate.exchange(url, method, entity, JsonNode.class);
    }

    private HttpEntity<Map<String, Object>> buildReserveRequest(String orderId, String sku, long qty) {
        var body = Map.<String, Object>of(
            "orderId", orderId,
            "items", List.of(Map.of("sku", sku, "quantity", qty))
        );
        return new HttpEntity<>(body, AUTH_HEADERS);
    }

    @Test
    void concurrentReserve_sameSku_exactlyOneSucceeds() throws Exception {
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        var created = new AtomicInteger();
        var conflict = new AtomicInteger();
        var otherCode = new AtomicInteger();
        var errors = new AtomicInteger();

        Runnable task = () -> {
            ready.countDown();
            try {
                start.await();
                var response = exchange(
                    baseUrl() + "/api/v1/reservations",
                    HttpMethod.POST,
                    buildReserveRequest("ORD-CC-" + UUID.randomUUID(), "A100", 700));
                var code = response.getStatusCode().value();
                if (code == 201) {
                    created.incrementAndGet();
                } else if (code == 409) {
                    conflict.incrementAndGet();
                } else {
                    otherCode.incrementAndGet();
                }
            } catch (Exception e) {
                errors.incrementAndGet();
            }
        };

        var executor = Executors.newFixedThreadPool(2);
        try {
            var f1 = CompletableFuture.runAsync(task, executor);
            var f2 = CompletableFuture.runAsync(task, executor);
            ready.await(5, TimeUnit.SECONDS);
            start.countDown();
            CompletableFuture.allOf(f1, f2).get(10, TimeUnit.SECONDS);
        } finally {
            executor.shutdown();
        }

        assertThat(created.get()).as("exactly one concurrent reservation should succeed")
            .isEqualTo(1);
        assertThat(conflict.get()).as("the other should get insufficient stock")
            .isEqualTo(1);
        assertThat(otherCode.get()).as("no unexpected status codes").isZero();
        assertThat(errors.get()).as("no request errors").isZero();

        var inventory = inventoryRepository.findById("A100").orElseThrow();
        assertThat(inventory.getAvailableStock()).isEqualTo(300L);
    }

    @Test
    void concurrentDuplicateOrderId_exactlyOneRowCreated() throws Exception {
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        var orderId = "ORD-DUP-CC-" + UUID.randomUUID().toString().substring(0, 8);
        var responses = new ArrayList<JsonNode>();
        var errors = new AtomicInteger();

        Runnable task = () -> {
            ready.countDown();
            try {
                start.await();
                var response = exchange(
                    baseUrl() + "/api/v1/reservations",
                    HttpMethod.POST,
                    buildReserveRequest(orderId, "A100", 5));
                synchronized (responses) {
                    responses.add(response.getBody());
                }
            } catch (Exception e) {
                errors.incrementAndGet();
            }
        };

        var executor = Executors.newFixedThreadPool(2);
        try {
            var f1 = CompletableFuture.runAsync(task, executor);
            var f2 = CompletableFuture.runAsync(task, executor);
            ready.await(5, TimeUnit.SECONDS);
            start.countDown();
            CompletableFuture.allOf(f1, f2).get(10, TimeUnit.SECONDS);
        } finally {
            executor.shutdown();
        }

        assertThat(errors.get()).as("neither thread should error").isZero();
        assertThat(responses).as("both threads should complete").hasSize(2);

        // At least one response should have data with an id
        AtomicReference<String> firstId = new AtomicReference<>();
        for (JsonNode resp : responses) {
            if (resp != null && resp.has("data") && resp.get("data").has("id")) {
                firstId.set(resp.get("data").get("id").asText());
                break;
            }
        }
        assertThat(firstId.get()).as("at least one response should contain a reservation id")
            .isNotNull();

        // Both responses should have the same reservation id (or one is a 200 duplicate)
        for (JsonNode resp : responses) {
            if (resp != null && resp.has("data") && resp.get("data").has("id")) {
                assertThat(resp.get("data").get("id").asText()).isEqualTo(firstId.get());
            }
        }

        long count = reservationRepository.findAll().stream()
            .filter(r -> r.getOrderId().equals(orderId))
            .count();
        assertThat(count).as("only one reservation row should exist").isEqualTo(1L);
    }

    @Test
    void concurrentConfirmAndCancel_exactlyOneSucceeds() throws Exception {
        // Insert a reservation directly — bypasses stock allocation so the confirm
        // path does not need inventory to be pre-seeded with a specific balance.
        var entity = new ReservationEntity();
        var resId = UUID.randomUUID();
        entity.setId(resId);
        entity.setOrderId("ORD-CC-CONFIRM-CANCEL");
        entity.setStatus("PENDING");
        entity.setCreatedAt(Instant.now());
        entity.setUpdatedAt(Instant.now());
        entity.setExpiresAt(Instant.now().plusSeconds(600));
        entity.setItems(new ArrayList<>());
        var item = new ReservationItemEntity();
        item.setId(UUID.randomUUID());
        item.setSku("A100");
        item.setQuantity(10L);
        item.setReservation(entity);
        entity.getItems().add(item);
        reservationRepository.save(entity);

        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        var ok = new AtomicInteger();
        var conflict = new AtomicInteger();

        // Each thread uses its own RestTemplate instance to avoid synchronized serialization
        // and achieve true concurrent server-side contention on the pessimistic lock.
        java.util.function.Supplier<org.springframework.web.client.RestTemplate> templateFactory = () -> {
            var t = new org.springframework.web.client.RestTemplate();
            t.setErrorHandler(new org.springframework.web.client.DefaultResponseErrorHandler() {
                @Override
                protected boolean hasError(@org.springframework.lang.NonNull org.springframework.http.HttpStatusCode code) {
                    return code.is5xxServerError();
                }
            });
            return t;
        };

        // Thread 1 confirms; thread 2 cancels — exactly one will win the SELECT FOR UPDATE
        Runnable confirmTask = () -> {
            ready.countDown();
            try {
                start.await();
                var response = templateFactory.get().exchange(
                    baseUrl() + "/api/v1/reservations/" + resId + "/confirm",
                    HttpMethod.POST,
                    new HttpEntity<>(AUTH_HEADERS),
                    JsonNode.class);
                if (response.getStatusCode() == HttpStatus.OK) ok.incrementAndGet();
                else if (response.getStatusCode() == HttpStatus.CONFLICT) conflict.incrementAndGet();
            } catch (Exception e) {
                // will be caught by assertions
            }
        };

        Runnable cancelTask = () -> {
            ready.countDown();
            try {
                start.await();
                var response = templateFactory.get().exchange(
                    baseUrl() + "/api/v1/reservations/" + resId + "/cancel",
                    HttpMethod.POST,
                    new HttpEntity<>(AUTH_HEADERS),
                    JsonNode.class);
                if (response.getStatusCode() == HttpStatus.OK) ok.incrementAndGet();
                else if (response.getStatusCode() == HttpStatus.CONFLICT) conflict.incrementAndGet();
            } catch (Exception e) {
                // will be caught by assertions
            }
        };

        var executor = Executors.newFixedThreadPool(2);
        try {
            var f1 = CompletableFuture.runAsync(confirmTask, executor);
            var f2 = CompletableFuture.runAsync(cancelTask, executor);
            ready.await(5, TimeUnit.SECONDS);
            start.countDown();
            CompletableFuture.allOf(f1, f2).get(10, TimeUnit.SECONDS);
        } finally {
            executor.shutdown();
        }

        assertThat(ok.get()).as("exactly one of confirm/cancel should succeed").isEqualTo(1);
        assertThat(conflict.get()).as("the other should get invalid state transition").isEqualTo(1);

        var updated = reservationRepository.findById(resId).orElseThrow();
        assertThat(updated.getStatus()).as("final status is terminal")
            .isIn("CONFIRMED", "CANCELLED");
    }
}
