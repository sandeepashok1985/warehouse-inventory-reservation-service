# Warehouse Inventory Reservation Service

**Java 21 · Spring Boot 3.3.4 · PostgreSQL 15 · NATS JetStream · Maven**

A high-throughput warehouse inventory reservation service that guarantees stock correctness under concurrent load.

---

## 1. Why This Problem

Warehouse inventory reservation sits at an intersection that's harder than it looks. You have to never sell stock you don't have — that's a hard constraint, not a performance goal. You have to handle concurrent clients colliding on the same SKU without corrupting state. And you have to recover from crashes without dropping events or leaking stock.

I picked this problem because it forces real architectural decisions, not academic ones. The concurrency strategy matters. The event model matters. The database schema has to enforce invariants the application can't. It's a small enough domain that a single engineer can build a complete solution, but rich enough that every trade-off has visible consequences. I wanted to work on something where being wrong means a warehouse ships the wrong inventory, not just a slow page load.

---

## 2. Architecture Overview

```
com.wirs.inventory.reservation/
├── api/               Inbound HTTP adapters — controllers, DTOs, security filter, error handler
├── application/       Use-case orchestration — services, expiry job, event publisher/subscribers
├── domain/            Pure business logic — model, state machine, factory, events, exceptions
└── infrastructure/    Outbound adapters — JPA entities/repos, Spring config, NATS messaging
```

The code follows a **hexagonal (ports & adapters)** layout with four layers and strict one-way dependencies:

```
API → Application → Domain (via interfaces)
Infrastructure → Application interfaces + Domain model
```

The domain layer (`domain/`) imports nothing from Spring or JPA. Not `@Entity`, not `@Service`, not `@Repository`. This isn't ideology — it has a concrete consequence: domain unit tests run without Spring context. A test for `PendingState.confirm()` starts in milliseconds and tests only the state logic.

**Why the boundaries are where they are.** The domain owns the business rules: state transitions, stock invariants, factory logic. The application layer orchestrates use cases without knowing about databases or HTTP. The API layer handles serialization, validation, and auth. The infrastructure layer bridges to PostgreSQL, NATS, and the scheduler. Each layer has exactly one reason to change. I drew the line between domain and everything else as strictly as possible because that's where the testing payoff is — the domain is where bugs cost real money, and I want those tests to run fast and often.

**Read paths bypass the domain model.** Write paths (create, confirm, cancel, expire) construct domain objects because they need state machine logic. Read paths (`getReservation`, `listReservations`, `getInventory`) return JPA projections directly, skipping the entity→domain→DTO double-mapping on the majority of request volume. There's no point paying an abstraction tax where no behavior is needed.

---

## 3. Framework Choice — Spring Boot

Spring Boot 3.3.x over Quarkus. The reasoning is practical.

Spring Data JPA's `@Version` annotation and `@Lock(LockModeType.PESSIMISTIC_WRITE)` integrate directly with the hybrid locking strategy this problem needs. Spring Security's filter chain gives a clean hook for API key validation without writing servlet infrastructure from scratch. Spring Retry's `@Retryable` with jittered backoff is production-grade retry logic in a single annotation. `@Scheduled` with `fixedDelay` handles the expiry job predictably.

Quarkus excels at native compilation and sub-100ms startup — neither is a primary concern for a long-running warehouse service. If this needed to cold-start in a serverless context every 30 seconds, Quarkus would win. It doesn't. Spring Boot's broader ecosystem, more predictable AOT behavior at moderate scale, and the team's familiarity with it make it the pragmatic choice.

---

## 4. Design Patterns

**State Pattern** — `com.wirs.inventory.reservation.domain.state.*`

`Reservation.confirm()` is three lines: call `state.confirm()`, assign the result, update the timestamp. No `if (status == PENDING)`. `PendingState` knows it can confirm. `ConfirmedState` knows it cannot. The rule is encoded in the state object, not scattered across conditionals. Adding a fourth state requires exactly one new class and zero changes to existing state classes or the aggregate.

**Factory Pattern** — `com.wirs.inventory.reservation.domain.factory.ReservationFactory`

Reservation construction involves: validating items are non-empty, validating quantities are positive, generating a UUID, computing `expiresAt = now + 10 minutes`, setting the initial `PendingState`. The factory owns all of that. The service receives a validated domain object ready for persistence. The factory injects a `Clock` for deterministic time handling in tests — no `Instant.now()` calls hidden in production code.

**Observer Pattern** — `com.wirs.inventory.reservation.application.event.*`

`ReservationService` calls `eventPublisher.publish(event)` and moves on. It doesn't know about `AuditEventSubscriber`, `StructuredLogSubscriber`, or `NatsEventPublisher`. Adding a Slack notification means writing one new class that implements `DomainEventSubscriber`. Zero changes to the service, zero changes to existing subscribers. `InProcessEventPublisher` fans out to all subscribers via Spring's `List<DomainEventSubscriber>` injection.

---

## 5. SOLID Principles

**Single Responsibility.** `ReservationService` orchestrates the workflow — it doesn't compute expiry times (that's `ReservationFactory`), manipulate stock numbers (that's `InventoryService`), map exceptions to HTTP codes (that's `GlobalExceptionHandler`), or validate API keys (that's `ApiKeyAuthenticationFilter`). Each class has one reason to change.

**Open/Closed.** The event subscriber system is the textbook example. `InProcessEventPublisher` iterates a `List<DomainEventSubscriber>`. Adding `NatsEventPublisher` for Advanced Track A creates one new class implementing that interface. `InProcessEventPublisher` doesn't change. `ReservationService` doesn't change. No existing code is touched.

**Dependency Inversion.** `ReservationService` depends on `EventPublisher` (an interface), not on `InProcessEventPublisher` (the concrete implementation). At test time, a mock `EventPublisher` is injected. At runtime, Spring injects the real fan-out publisher. The high-level orchestration never depends on low-level infrastructure choices.

---

## 6. Database Design

Six tables, managed through Liquibase SQL changesets:

| Table | Key Columns | Design Rationale |
|-------|-------------|-----------------|
| `products` | `sku VARCHAR(50) PK` | Static catalog; referential integrity anchor |
| `inventory` | `available_stock BIGINT`, `reserved_stock BIGINT`, `version BIGINT` | `BIGINT` because warehouse quantities exceed INT range; `CHECK (total_stock = available_stock + reserved_stock)` enforces the immutable stock invariant at the database level as a safety net |
| `reservations` | `id UUID PK`, `order_id VARCHAR(100) UNIQUE`, `status VARCHAR(20)` | UUIDs don't expose sequential IDs; the unique constraint on `order_id` is the atomic idempotency guarantee |
| `reservation_items` | `reservation_id UUID FK`, `sku VARCHAR(50) FK`, `quantity BIGINT` | Value objects with no identity; composite unique key prevents duplicate SKU entries |
| `reservation_events` | `id UUID PK`, `payload JSONB`, `published_at TIMESTAMP NULL` | JSONB allows evolving event structure without schema migrations; nullable `published_at` is the outbox flag |
| `reservation_expiry_state` | `id INT CHECK (id = 1)`, `processing_in_progress BOOLEAN` | Single-row coordination table; no `@Version` because this uses pessimistic locking, not optimistic |

**Key indexes:**
- Partial index `idx_reservations_status_expiry` on `(status, expires_at) WHERE status = 'PENDING'` — used exclusively by the expiry job, stays small by excluding terminal states
- Partial index `idx_reservation_events_unpublished` on `(created_at) WHERE published_at IS NULL` — keeps the outbox relay query fast as the table grows

**Partitioning.** `reservation_events` is partitioned by day (`RANGE (created_at)`). At 10,000 reservations/minute, the table accumulates ~14.4M rows/day. Dropping old partitions is a metadata operation, not a delete storm. Published events older than 7 days are cleaned up by a weekly batch delete job.

---

## 7. Concurrency Strategy

Two genuinely different concurrency problems, two genuinely different solutions.

**Inventory allocation uses optimistic locking** (`@Version` column + `@Retryable` with jittered backoff). Contention per SKU is rare at scale — most requests target different SKUs. Reads proceed freely; conflicts only happen at the update boundary. The retry uses `@Backoff(delay = 100, multiplier = 2, maxDelay = 1000, random = true)`. The `random = true` (jitter) is critical: without it, 500 retrying threads all wake up at 100ms simultaneously and collide again. With jitter, wakeups spread across the backoff band.

**State transitions use pessimistic locking** (`@Lock(PESSIMISTIC_WRITE)` → `SELECT FOR UPDATE`). A PENDING reservation has high collision probability (the same reservation can be confirmed or cancelled, and clients retry). With optimistic locking, retry semantics are ambiguous — should a confirm retry after a concurrent cancel? The answer is "read the current state and decide," which is exactly what `SELECT FOR UPDATE` gives you. Deterministic, fail-fast, no retry loop.

**Multi-SKU deadlock prevention.** `SkuAllocationOrder` is a typed value object whose only public constructor sorts items by SKU before building the record. `InventoryService.allocateStock()` accepts only this type — passing an unsorted list doesn't compile. Same pattern with `OrderedIdList` for any future bulk reservation operations. The lock acquisition order is enforced at the type level, not by convention.

**Multi-SKU partial failure.** If a reservation targets SKU A (available) and SKU B (insufficient), the entire transaction rolls back. `@Transactional` guarantees that SKU A's allocation reverts. No partial allocation. The stock invariant holds.

---

## 8. Idempotency

Two-layer guarantee for `POST /api/v1/reservations`:

1. **Application check:** Before attempting creation, the service queries `findByOrderId()`. If found, it returns HTTP 200 with the existing reservation — the caller gets the same response as if their request had succeeded.

2. **Database constraint:** Two concurrent requests with the same `orderId` may both pass the application check (neither has committed). The `UNIQUE (order_id)` constraint means only one INSERT succeeds. The other gets a `DataIntegrityViolationException`, which the service catches, re-reads the winning reservation, and returns it as HTTP 200.

The constraint is atomic at the database level — no advisory locks, no `SELECT FOR UPDATE` on a non-existent row, no gap-lock complexity. HTTP 200 for duplicates (not 409) because returning an error on a successful retry is misleading — the operation worked, nothing new was created.

---

## 9. Event Design

Events are domain records (`ReservationCreatedEvent`, `ReservationConfirmedEvent`, `ReservationCancelledEvent`) implementing a `DomainEvent` interface. They carry the aggregate ID, timestamp, and a type-specific payload.

**Transactional Outbox.** Every event is written to `reservation_events` in the same database transaction as the state change. If the commit succeeds, the event is durable. If the application crashes between the commit and the in-process dispatch, the event sits in the table with `published_at = NULL`. A background `ReservationEventRelay` polls every 30 seconds for unpublished events and delivers them. No event is ever lost.

### Advanced Track A — NATS JetStream

When `app.nats.enabled=true`, `NatsEventPublisher` joins the subscriber chain and publishes each event to its NATS subject:

| Event | Subject |
|-------|---------|
| `ReservationCreatedEvent` | `reservations.created` |
| `ReservationConfirmedEvent` | `reservations.confirmed` |
| `ReservationCancelledEvent` | `reservations.cancelled` |

**Stream configuration:** The `RESERVATIONS` stream is file-backed (survives restarts), retains messages for 7 days (up to 10 GB), uses `DiscardPolicy.New` (rejects new publishes when full rather than silently dropping old ones), and has a 2-minute server-side deduplication window via `messageId`.

**Consumer design:** Two durable consumer groups. `wirs-order-mgmt` subscribes to `reservations.created` and `reservations.cancelled` with `AckExplicit`, 30s `ackWait`, max 5 redeliveries. `wirs-analytics` subscribes to all subjects with 60s `ackWait`, max 10 redeliveries. Both use `DeliverAll` policy — they receive messages missed during downtime.

**Ack strategy:** `PublishAck` from JetStream confirms server-side persistence. If the ACK doesn't arrive within the timeout, the relay retries with the same `messageId`; JetStream deduplicates within the 2-minute window. Consumers use `AckExplicit` — messages are removed only after explicit acknowledgment. If a consumer crashes before ACKing, the message is redelivered after `ackWait` expires.

**NATS unavailable:** `NatsEventPublisher.on()` catches `IOException` and logs a warning — it does not throw. The event remains in `reservation_events` with `published_at = NULL`. The HTTP client receives a 201 as normal (NATS failure is invisible to callers). The `ReservationEventRelay` redelivers once NATS recovers. Events cannot be lost because they were persisted in the database before the NATS publish was attempted.

---

## 10. Advanced Track B — Redis (Design, Not Implemented)

This track is designed but not implemented in the current codebase. The design is documented in `ARCHITECTURE.md` for reference.

**Read-through cache:** `inventory:sku:{sku}` key with 30-second TTL. Cache is invalidated on any inventory write (allocate/release) via immediate `redisTemplate.delete()`. Fallback to PostgreSQL on Redis outage.

**Distributed lock for expiry:** Redisson `RLock` with 90-second TTL on key `lock:expiry-job`. The lock TTL is shorter than the 120-second job interval, so a crashed instance's lock auto-expires before the next interval. Wait timeout is 0 — skip immediately if lock is held. Falls back to the database coordination table if Redis is unavailable.

---

## 11. Expiry Job Design

The `ReservationExpiryJob` runs every 2 minutes (`@Scheduled(fixedDelay = 120_000)` — not cron, because `fixedDelay` naturally staggers instances that finish at different times).

**Coordination:** A single-row table `reservation_expiry_state` with `processing_in_progress` flag. The job acquires the row lock via `SELECT FOR UPDATE SKIP LOCKED`. If the row is already locked, `SKIP LOCKED` returns immediately — the losing instance returns in microseconds without consuming a connection slot. Without `SKIP LOCKED`, every instance would queue on the locked row, holding connections and threads for the entire expiry duration.

**Stuck-job detection:** If `processing_in_progress = TRUE` and `last_expiry_run` is more than 5 minutes old, the flag is considered stale (the previous instance crashed) and the job overrides it. No manual intervention required.

**Per-reservation error handling:** A `try/catch` wraps each individual reservation processing step. If reservation R1 fails (e.g., a concurrent API confirm completed between the scan and the lock), the job logs the failure and moves to R2. One failure does not abort the batch.

**Batch size:** Processed in batches of 100 via `LIMIT 100` on the expiry query. After a 30-minute outage with 50,000 expired reservations, the backlog clears across multiple runs without starving live traffic of database connections.

---

## 12. Security

**ApiKeyAuthenticationFilter** extends `OncePerRequestFilter`. It extracts the `X-API-Key` header, validates it against a comma-separated list of valid keys from `app.security.api-keys`, and either sets an `Authentication` object in `SecurityContextHolder` or writes a 401 response directly.

Multiple keys are supported by design — rotating keys in production requires adding the new key before removing the old one. Zero-downtime key rotation.

All paths except `/health`, `/swagger-ui.html`, and `/v3/api-docs/**` require authentication. `HealthController` is a separate public endpoint with no auth.

---

## 13. How to Run

**Prerequisites:** Java 21+, Docker & Docker Compose, Maven Wrapper (`mvnw`) included.

```bash
# Core Track (PostgreSQL only)
docker compose up
# API at http://localhost:8080, Swagger at http://localhost:8080/swagger-ui.html

# Advanced Track A (PostgreSQL + NATS JetStream)
docker compose -f docker-compose.yml -f docker-compose.nats.yml up
```

**Configuration** is via environment variables:

| Variable | Default | Purpose |
|----------|---------|---------|
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://localhost:5432/inventory_db` | Database JDBC URL |
| `SPRING_DATASOURCE_USERNAME` | `app_user` | Database user |
| `SPRING_DATASOURCE_PASSWORD` | `secure_dev_password` | Database password |
| `API_KEYS` | `dev-key-12345` | Comma-separated valid API keys |
| `NATS_ENABLED` | `false` | Enable NATS JetStream (Advanced A) |
| `NATS_URL` | `nats://localhost:4222` | NATS server URL |

Liquibase runs automatically on startup. No manual schema setup needed.

---

## 14. How to Run Tests

```bash
# Unit tests only — no Docker, no Spring context for domain tests
./mvnw test -Dgroups=unit

# Integration tests — full Spring context with Testcontainers (requires Docker)
./mvnw test -Dgroups=integration

# Full pipeline: compile → all tests → JaCoCo coverage check (≥80%)
./mvnw verify

# Coverage report (after verify)
open target/site/jacoco/index.html
```

Unit tests cover: state machine transitions (every state handles every transition correctly), stock allocation and release invariants, factory validation (empty items, zero quantities, expiry time calculation), event publishing, idempotency, expiry job logic, exception-to-HTTP mapping, and API key filter behavior. 96+ tests across 22+ test classes.

Integration tests use Testcontainers to spin up PostgreSQL in a container. They cover: concurrent stock reservation (exactly one succeeds), concurrent confirm vs cancel (one outcome wins), full lifecycle end-to-end, stock restoration on cancel, multi-SKU atomic rollback, expiry releases stock, pagination enforcement, and security boundary enforcement.

---

## 15. Trade-offs

**No full Event Sourcing.** The `reservation_events` table is an audit log, not an event store for state reconstruction. Full event sourcing would require snapshot management, schema versioning, and projection rebuild performance optimization — real operational overhead for a system where the primary need is audit trail and downstream delivery. The outbox gives us both without the complexity. The trade-off: we can't rewind state or rebuild projections from scratch without data loss of terminal states.

**Synchronous event publishing in-core.** In-process subscribers run synchronously within the transaction. A slow subscriber (say, a blocked NATS connection) delays the HTTP response. In production, the NATS publisher should use a bounded async queue. For the core track, this simplification is acceptable because the subscribers are doing local work (DB writes and logging). The risk: a subscriber throwing an uncaught exception could roll back the transaction, losing the business operation. This is mitigated by wrapping subscriber calls in try/catch at the publisher level.

**Database-only expiry coordination.** `SELECT FOR UPDATE SKIP LOCKED` on a single coordination row works correctly for 2-10 instances. Beyond that, row-level contention on the coordination row becomes measurable. The Redis distributed lock (Advanced Track B) is the right answer at larger scale. For the expected deployment size, the database approach is simpler and has one fewer infrastructure dependency to fail.

**No circuit breaker for database calls.** If PostgreSQL becomes degraded (slow queries, connection timeouts), the application has no mechanism to shed load gracefully. A Resilience4j CircuitBreaker wrapping database operations would allow the service to fail fast with a 503 instead of exhausting the connection pool. This is production-necessary but was outside the core track scope.

**Read paths bypass the domain model.** This creates an asymmetry: read and write paths use different code paths. A write followed by an immediate read returns data that was never validated by the domain model. In practice, the data was validated at write time and is stored in the same database — the risk is minimal. The benefit is avoiding entity→domain→DTO double-mapping on 90%+ of request volume (reads).

---

## 16. What Breaks at 10,000 Concurrent Requests Per Minute

167 requests per second. Here's the ordered failure cascade:

**First failure — HikariCP connection pool exhaustion.** Little's Law says 167 RPS × 200ms average DB operation = 33 connections steady-state. But warehouse workloads arrive in bursts — a WES releasing 500 pick orders simultaneously drives instantaneous demand far higher. Pool size of 50 covers burst headroom, but sustained bursts exceeding that cause threads to queue. With `connection-timeout = 2000`, they fail fast with a 503 after 2 seconds. The failure mode is HTTP 503 responses with `HikariPool-1 - Connection is not available, request timed out after 2000ms` in logs. The fix: increase pool size monitoring `hikaricp.connections.usage` — alert above 80% sustained.

**Second failure — optimistic lock contention on hot SKUs.** If the burst concentrates on 2-3 popular SKUs, version-column retries climb. Each retry takes 100-400ms with jittered backoff. At 500+ concurrent requests on a single SKU, the retry pool becomes a latency amplifier. The failure mode is threads burning retry attempts, with `ObjectOptimisticLockingFailureException` appearing in logs after all 3 retries are exhausted. Clients receive 409 INSUFFICIENT_STOCK even when stock exists — because the retries all failed before the last update could commit. The fix at current scale: monitor `reservation.stock.optimistic_lock_retries` and alert above 20% retry rate. The structural fix: Redis DECR-based atomic stock (Advanced Track B), which eliminates version-column races entirely.

**Third failure — expiry job I/O after recovery.** If the service goes down for 30 minutes, it comes back to potentially 50,000 expired reservations. The expiry job processes them in batches of 100 per 2-minute cycle. Each reservation requires a lock, state update, inventory release (N SKUs), and event insert. At 50 per second processing rate, the backlog takes ~16 minutes to clear. The failure mode during this period is the expiry job competing with live traffic for the connection pool. The fix is already in place: `LIMIT 100` per run prevents the job from consuming all connections at once.

**Fourth failure — GC pressure.** At 167 RPS, each request creates multiple short-lived objects (request/response DTOs, service objects, repository objects, domain aggregates, events). G1GC handles this up to a point, then P99 latency starts creeping while P50 looks fine. The fix: switch to ZGC (`-XX:+UseZGC`) for sub-millisecond pause times at high allocation rates, and increase heap to 2-4 GB.

**What stays stable:** Tomcat's default 200-thread pool handles 167 RPS comfortably. PostgreSQL MVCC with row-level locking scales to thousands of concurrent readers. The partial indexes on `reservation_events` and `reservations` remain efficient at this volume. The database itself is not the first failure point.

---

## API Reference

### Endpoints

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| `POST` | `/api/v1/reservations` | Required | Reserve inventory (idempotent by `orderId`) |
| `POST` | `/api/v1/reservations/{id}/confirm` | Required | Confirm a PENDING reservation |
| `POST` | `/api/v1/reservations/{id}/cancel` | Required | Cancel a PENDING reservation |
| `GET` | `/api/v1/reservations/{id}` | Required | Get reservation by ID |
| `GET` | `/api/v1/reservations?page=0&size=20` | Required | List reservations (paginated) |
| `GET` | `/api/v1/inventory/{sku}` | Required | Get stock levels for a SKU |
| `GET` | `/health` | Public | Health check |

### Error Codes

| HTTP Status | Code | Description |
|-------------|------|-------------|
| 200 | `DUPLICATE_ORDER` | orderId already exists; returns existing reservation |
| 201 | — | Reservation created |
| 400 | `INVALID_REQUEST` | Validation failure |
| 401 | `UNAUTHORIZED` | Missing or invalid API key |
| 404 | `RESERVATION_NOT_FOUND` | Reservation does not exist |
| 404 | `SKU_NOT_FOUND` | SKU does not exist |
| 409 | `INSUFFICIENT_STOCK` | Not enough available stock |
| 409 | `INVALID_STATE_TRANSITION` | Invalid lifecycle transition |
| 500 | `INVENTORY_NOT_INITIALIZED` | Data integrity failure |

### Reservation Lifecycle

```
PENDING → CONFIRMED  (terminal — stock committed to fulfillment)
PENDING → CANCELLED  (terminal — stock released back to available pool)
PENDING → CANCELLED  (automatic — TTL expiry job, triggeredBy = EXPIRY_JOB)
```

### Response Envelope

All responses use a consistent `{ data, error }` envelope:

```json
{
  "data": { "reservationId": "...", "orderId": "ORD-1001", "status": "PENDING" },
  "error": null
}
```

```json
{
  "data": null,
  "error": {
    "code": "INSUFFICIENT_STOCK",
    "message": "SKU A100 has only 30 units available; 50 were requested"
  }
}
```
