# Warehouse Inventory Reservation Service — Implementation Plan

**Author:** Senior Staff Software Engineer  
**Date:** May 2026  
**Revision:** Current  
**Derived from:** ARCHITECTURE.md  
**Stack:** Java 21 · Spring Boot 3.3.x · PostgreSQL 15 · NATS JetStream · Maven  
**Coding Standard:** Google Java Style Guide · Max line length: 120 characters  

---

## Table of Contents

1. [How to Read This Plan](#1-how-to-read-this-plan)
2. [Global Constraints & Coding Rules](#2-global-constraints--coding-rules)
3. [Chunk 1 — Project Scaffold & Build Infrastructure](#chunk-1--project-scaffold--build-infrastructure)
4. [Chunk 2 — Liquibase Database Migrations](#chunk-2--liquibase-database-migrations)
5. [Chunk 3 — Domain Model (Pure Java)](#chunk-3--domain-model-pure-java)
6. [Chunk 4 — Domain State Machine](#chunk-4--domain-state-machine)
7. [Chunk 5 — Domain Events & Exceptions](#chunk-5--domain-events--exceptions)
8. [Chunk 6 — Infrastructure: JPA Entities](#chunk-6--infrastructure-jpa-entities)
9. [Chunk 7 — Infrastructure: JPA Repositories](#chunk-7--infrastructure-jpa-repositories)
10. [Chunk 8 — Infrastructure: Configuration Beans](#chunk-8--infrastructure-configuration-beans)
11. [Chunk 9 — Application: Event Publisher & Subscribers](#chunk-9--application-event-publisher--subscribers)
12. [Chunk 10 — Application: InventoryService](#chunk-10--application-inventoryservice)
13. [Chunk 11 — Application: ReservationService (Core)](#chunk-11--application-reservationservice-core)
14. [Chunk 12 — Application: ReservationExpiryJob](#chunk-12--application-reservationexpiryjob)
15. [Chunk 12b — Application: Core Outbox Relay](#chunk-12b--application-core-outbox-relay)
16. [Chunk 13 — API: DTOs & Validation](#chunk-13--api-dtos--validation)
17. [Chunk 14 — API: Controllers & Exception Handler](#chunk-14--api-controllers--exception-handler)
18. [Chunk 15 — API: Security Filter & Configuration](#chunk-15--api-security-filter--configuration)
19. [Chunk 16 — Integration Tests (Core Track)](#chunk-16--integration-tests-core-track)
20. [Chunk 17 — NATS JetStream: Infrastructure Beans](#chunk-17--nats-jetstream-infrastructure-beans)
21. [Chunk 18 — NATS JetStream: NatsEventPublisher & StreamInitializer](#chunk-18--nats-jetstream-natseventpublisher--streaminitializer)
22. [Chunk 19 — NATS JetStream: Outbox Relay](#chunk-19--nats-jetstream-outbox-relay)
23. [Chunk 20 — NATS JetStream: Integration Tests](#chunk-20--nats-jetstream-integration-tests)
24. [Chunk 21 — Docker Compose & Operational Wiring](#chunk-21--docker-compose--operational-wiring)
25. [TDD Cycle Reference](#tdd-cycle-reference)
26. [Definition of Done Checklist](#definition-of-done-checklist)

---

## 1. How to Read This Plan

Each chunk is a self-contained unit of work that can be implemented and tested independently. Chunks are ordered
by dependency: a chunk may only depend on chunks that precede it. No chunk is optional except Chunks 17–20
(Advanced Track A — NATS JetStream), which depend on the core track being complete.

Every chunk follows the **Red → Green → Refactor** TDD cycle:

1. **Write the tests first** — failing tests define the contract before any implementation.
2. **Write the minimum implementation** to make tests pass.
3. **Refactor** for clarity and quality without breaking any test.
4. Mark the chunk as done only when all tests in the chunk are green and code review passes.

**Coding phase instructions are embedded in each chunk.** The coder must not look ahead to future chunks when
implementing — the interface between chunks is designed to be discovered incrementally through the test suite.

---

## 2. Global Constraints & Coding Rules

These constraints apply to every line of code written. Violations must be fixed before marking any chunk done.

### 2.1 Layer Dependency Rules (ENFORCED — zero exceptions)

```
API Layer       →  Application Layer only
Application     →  Domain Layer only (via interfaces)
Domain Layer    →  NO external imports (no org.springframework.*, no jakarta.persistence.*)
Infrastructure  →  Application interfaces + Domain model
```

The domain layer (`com.wirs.inventory.reservation.domain`) must contain **zero** Spring annotations and
**zero** JPA annotations. Verify with:
```bash
grep -r "org.springframework" src/main/java/com/wirs/inventory/reservation/domain/
# Must return zero results.
```

### 2.2 Java 21 Requirements

- Use **records** for all DTOs, events, and value objects (immutable by construction).
- Use **sealed interfaces** where the complete set of subtypes is known at compile time (e.g., `ReservationState`
  variants may use sealed classes).
- Use **pattern matching** (`instanceof` with binding variable, `switch` expressions) where it simplifies code.
- Use **virtual threads** (`spring.threads.virtual.enabled=true`) — no manual thread pool configuration for
  HTTP request handling.
- Use `var` for local variable type inference where the right-hand side type is unambiguous.

### 2.3 Google Java Style — Key Rules

- **Line length:** 120 characters maximum. Configure checkstyle enforcement.
- **Indentation:** 4 spaces, no tabs.
- **Braces:** Always use braces for `if`, `else`, `for`, `while` blocks — no one-liners without braces.
- **Imports:** Static imports for test assertions only (`assertThat`, `verify`, etc.). No wildcard imports
  except `import static org.assertj.core.api.Assertions.*` in tests.
- **Naming conventions:**
  - Classes: `UpperCamelCase`
  - Methods / variables: `lowerCamelCase`
  - Constants: `UPPER_SNAKE_CASE`
  - Packages: `lowercase.nodots`

### 2.4 Documentation Rules

- **No multi-paragraph Javadoc blocks.** One-line summary Javadoc only on public API methods.
- **No `@author`, `@date`, or `@version` Javadoc tags** — git blame and git log are authoritative.
- **No `// TODO:` comments in committed code** — use GitHub Issues or the ticket system.
- **Inline comments must explain WHY**, never WHAT. Delete any comment that restates the code.
- **Class-level Javadoc** is required on all public classes. One sentence: what this class does.
- **Method-level Javadoc** is required only on public interface methods and public service methods.
  Format: one-line summary + `@throws` for each checked or documented exception.
- **No commented-out code** — if code is removed, remove it completely.

### 2.5 Testing Rules

- **Unit tests:** No Spring context, no database, no network. Use Mockito for dependencies. Annotate with
  `@Tag("unit")`.
- **Integration tests:** Full Spring context, real PostgreSQL via Testcontainers. Annotate with
  `@Tag("integration")`.
- **Naming convention:** `MethodUnderTest_Scenario_ExpectedBehavior` (e.g.,
  `allocate_insufficientStock_throwsInsufficientStockException`).
- **AssertJ** is the exclusive assertion library. JUnit 5 `Assertions` are permitted only for `assertDoesNotThrow`
  and `assertThrows` where AssertJ equivalents are verbose.
- **No `Thread.sleep()` in tests** except the one documented concurrent integration test that requires it
  (see Chunk 16). All other time-based behavior is tested via injected `Clock`.

### 2.6 Exception Handling Rules

- **Controllers throw domain exceptions.** They do not catch anything. Period.
- **`GlobalExceptionHandler`** is the single location where domain exceptions map to HTTP responses.
- **Never swallow exceptions silently.** Either re-throw, log + continue (in batch jobs), or map to a
  structured response.
- **Never use `Exception` or `Throwable` as a catch type** unless explicitly catching infrastructure
  failures (e.g., `IOException` from NATS client).

### 2.7 Transaction Rules

- `@Transactional` on **service methods**, not on repository methods (Spring Data already handles those).
- All reservation creation, confirmation, cancellation, and expiry operations are a single
  `@Transactional` boundary — the state change and the event record commit atomically.
- Use `@Transactional(readOnly = true)` on read-only service methods to avoid write lock acquisition
  and to enable read replica routing in future.

---

## Chunk 1 — Project Scaffold & Build Infrastructure

**Objective:** Create a working Maven project that compiles, passes checkstyle, and starts up against a
PostgreSQL database with no application logic yet.

**Dependencies:** None — this is the foundation.

---

### 1.1 Maven pom.xml

Create `pom.xml` in the project root. Exact configuration:

```xml
<!-- Root declarations -->
<groupId>com.wirs.inventory</groupId>
<artifactId>reservation-service</artifactId>
<version>1.0.0-SNAPSHOT</version>
<packaging>jar</packaging>

<!-- Parent -->
<parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>3.3.4</version>
</parent>

<!-- Properties -->
<properties>
    <java.version>21</java.version>
    <testcontainers.version>1.19.8</testcontainers.version>
    <jnats.version>2.17.6</jnats.version>
</properties>
```

**Dependencies to include:**

| Artifact | Scope | Purpose |
|----------|-------|---------|
| `spring-boot-starter-web` | compile | Spring MVC + embedded Tomcat |
| `spring-boot-starter-data-jpa` | compile | Spring Data JPA + Hibernate 6 |
| `spring-boot-starter-security` | compile | Filter chain for API key auth |
| `spring-boot-starter-validation` | compile | Bean Validation 3 (`@Valid`, `@NotBlank`) |
| `spring-boot-starter-actuator` | compile | Micrometer metrics + health endpoints |
| `spring-retry` | compile | `@Retryable` for optimistic lock retries |
| `aspectjweaver` | compile | Required for `@Retryable` AOP proxy |
| `liquibase-core` | compile | Database migrations on startup |
| `postgresql` | runtime | JDBC driver |
| `springdoc-openapi-starter-webmvc-ui` | compile | OpenAPI 3 + Swagger UI |
| `logstash-logback-encoder` | compile | Structured JSON logging |
| `io.nats:jnats` | compile | NATS Java client (version pinned: 2.17.6) |
| `spring-boot-starter-test` | test | JUnit 5, Mockito, AssertJ |
| `testcontainers:junit-jupiter` | test | Testcontainers integration |
| `testcontainers:postgresql` | test | PostgreSQL container for integration tests |
| `testcontainers:nats` | test | NATS container for NATS integration tests |

**Maven plugins to configure:**

1. `spring-boot-maven-plugin` — repackage goal for fat JAR.
2. `maven-checkstyle-plugin` — fail build on style violations. Use `google_checks.xml`. Set
   `maxLineLength` to 120 in checkstyle config override.
3. `maven-surefire-plugin` — configure groups: `unit` runs with `-Dgroups=unit`,
   `integration` runs with `-Dgroups=integration`. Default: run both.
4. `jacoco-maven-plugin` — bind `prepare-agent` to `initialize` phase, `report` to
   `verify` phase. Minimum coverage threshold: 80% instruction coverage.

---

### 1.2 Application Entry Point

File: `src/main/java/com/wirs/inventory/reservation/ReservationServiceApplication.java`

```java
/** Entry point for the Warehouse Inventory Reservation Service. */
@SpringBootApplication
public class ReservationServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(ReservationServiceApplication.class, args);
    }
}
```

---

### 1.3 application.yml

File: `src/main/resources/application.yml`

```yaml
spring:
  application:
    name: warehouse-inventory-reservation-service
  datasource:
    url: ${SPRING_DATASOURCE_URL:jdbc:postgresql://localhost:5432/inventory_db}
    username: ${SPRING_DATASOURCE_USERNAME:app_user}
    password: ${SPRING_DATASOURCE_PASSWORD:secure_dev_password}
    hikari:
      maximum-pool-size: 50
      minimum-idle: 10
      connection-timeout: 20000
      idle-timeout: 300000
      max-lifetime: 1200000
  jpa:
    hibernate:
      ddl-auto: validate          # Liquibase owns schema; Hibernate only validates
    show-sql: false
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQLDialect
        format_sql: false
  liquibase:
    change-log: classpath:db/changelog/db.changelog-master.xml
  lifecycle:
    timeout-per-shutdown-phase: 30s
  threads:
    virtual:
      enabled: true               # Java 21 virtual threads for HTTP request handling

server:
  port: 8080
  shutdown: graceful

app:
  security:
    api-keys: ${API_KEYS:dev-key-12345}
  reservation:
    expiry-minutes: 10
  nats:
    enabled: ${NATS_ENABLED:false}
    url: ${NATS_URL:nats://localhost:4222}

management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  endpoint:
    health:
      show-details: when-authorized
```

Also create `src/test/resources/application-test.yml`:

```yaml
# Test profile — Testcontainers overrides datasource via @DynamicPropertySource
spring:
  liquibase:
    enabled: true
  jpa:
    show-sql: true
app:
  nats:
    enabled: false
```

---

### 1.4 TDD — Chunk 1 Tests

**Test class:** `ReservationServiceApplicationTest.java` (`@Tag("integration")`)

```
Test: contextLoads
  - Assert Spring context starts without exception.
  - Uses: @SpringBootTest, PostgreSQLContainer via BaseIntegrationTest (from Chunk 16).
  - Purpose: Smoke test. If this fails, the build is broken at infrastructure level.
```

> **Coding instruction:** Write this test first. It will fail until the full project scaffold exists.
> The test should be in `src/test/java/com/wirs/inventory/reservation/` and extend
> `BaseIntegrationTest` (created in Chunk 16). For Chunk 1 only, use a minimal test that just
> verifies the main class can be instantiated. Upgrade to full context load in Chunk 16.

---

## Chunk 2 — Liquibase Database Migrations

**Objective:** All database tables exist and match the schema in ARCHITECTURE.md. The application
starts against a fresh PostgreSQL database and Liquibase applies all migrations successfully.

**Dependencies:** Chunk 1 (Maven project, `application.yml`, Liquibase dependency).

---

### 2.1 Master Changelog

File: `src/main/resources/db/changelog/db.changelog-master.xml`

```xml
<?xml version="1.0" encoding="UTF-8"?>
<databaseChangeLog
    xmlns="http://www.liquibase.org/xml/ns/dbchangelog"
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
    xsi:schemaLocation="http://www.liquibase.org/xml/ns/dbchangelog
    http://www.liquibase.org/xml/ns/dbchangelog/dbchangelog-4.20.xsd">

    <include file="db/changelog/V001__create_products.sql"/>
    <include file="db/changelog/V002__create_inventory.sql"/>
    <include file="db/changelog/V003__create_reservations.sql"/>
    <include file="db/changelog/V004__create_reservation_items.sql"/>
    <include file="db/changelog/V005__create_reservation_events.sql"/>
    <include file="db/changelog/V006__create_expiry_state.sql"/>
    <include file="db/changelog/V007__seed_initial_products.sql"/>
    <include file="db/changelog/V008__reservation_events_initial_partitions.sql"/>
</databaseChangeLog>
```

### 2.2 Changeset Files

Each file must start with the Liquibase formatted SQL header. Include a `rollback` comment.

**V001__create_products.sql**
```sql
-- liquibase formatted sql
-- changeset wirs:V001-create-products
CREATE TABLE products (
    sku         VARCHAR(50)  PRIMARY KEY,
    name        VARCHAR(255) NOT NULL,
    description TEXT,
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
-- rollback DROP TABLE products;
```

**V002__create_inventory.sql**
```sql
-- liquibase formatted sql
-- changeset wirs:V002-create-inventory
CREATE TABLE inventory (
    sku              VARCHAR(50) PRIMARY KEY REFERENCES products(sku),
    total_stock      BIGINT      NOT NULL CHECK (total_stock >= 0),
    available_stock  BIGINT      NOT NULL CHECK (available_stock >= 0),
    reserved_stock   BIGINT      NOT NULL CHECK (reserved_stock >= 0),
    version          BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT stock_balance CHECK (total_stock = available_stock + reserved_stock)
);
CREATE INDEX idx_inventory_sku_available ON inventory(sku) WHERE available_stock > 0;
-- rollback DROP TABLE inventory;
```

**V003__create_reservations.sql**
```sql
-- liquibase formatted sql
-- changeset wirs:V003-create-reservations
CREATE TABLE reservations (
    id         UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id   VARCHAR(100) NOT NULL,
    status     VARCHAR(20)  NOT NULL
                   CHECK (status IN ('PENDING', 'CONFIRMED', 'CANCELLED')),
    created_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP    NOT NULL,
    CONSTRAINT uq_reservations_order_id UNIQUE (order_id)
);
CREATE INDEX idx_reservations_order_id ON reservations(order_id);
CREATE INDEX idx_reservations_status_expiry
    ON reservations(status, expires_at) WHERE status = 'PENDING';
-- rollback DROP TABLE reservations;
```

**V004__create_reservation_items.sql**
```sql
-- liquibase formatted sql
-- changeset wirs:V004-create-reservation-items
CREATE TABLE reservation_items (
    id             UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    reservation_id UUID        NOT NULL REFERENCES reservations(id) ON DELETE CASCADE,
    sku            VARCHAR(50) NOT NULL REFERENCES products(sku),
    quantity       BIGINT      NOT NULL CHECK (quantity > 0),
    CONSTRAINT uq_reservation_sku UNIQUE (reservation_id, sku)
);
CREATE INDEX idx_reservation_items_reservation_id ON reservation_items(reservation_id);
-- rollback DROP TABLE reservation_items;
```

**V005__create_reservation_events.sql**
```sql
-- liquibase formatted sql
-- changeset wirs:V005-create-reservation-events
-- At 10K reservations/minute the table grows ~14.4M rows/day. Partitioned by day so old
-- partitions can be dropped as a metadata-only operation — no table scan, no bloat.
CREATE TABLE reservation_events (
    id             UUID        NOT NULL DEFAULT gen_random_uuid(),
    reservation_id UUID        NOT NULL REFERENCES reservations(id) ON DELETE CASCADE,
    event_type     VARCHAR(50) NOT NULL
                       CHECK (event_type IN ('CREATED', 'CONFIRMED', 'CANCELLED')),
    payload        JSONB       NOT NULL,
    created_at     TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    published_at   TIMESTAMP   NULL,
    PRIMARY KEY (id, created_at)
) PARTITION BY RANGE (created_at);

-- Seed partition covering the first 30 days from today; extend via the runbook.
CREATE TABLE reservation_events_default PARTITION OF reservation_events DEFAULT;

CREATE INDEX idx_reservation_events_reservation_id ON reservation_events(reservation_id);
CREATE INDEX idx_reservation_events_unpublished
    ON reservation_events(created_at) WHERE published_at IS NULL;
-- rollback DROP TABLE reservation_events;
```

**V008__reservation_events_initial_partitions.sql**
```sql
-- liquibase formatted sql
-- changeset wirs:V008-reservation-events-initial-partitions
-- Create explicit daily partitions for the current and next 30 days.
-- Operations team runs this monthly; old partitions are dropped via:
--   DROP TABLE reservation_events_YYYYMMDD;  (zero lock, zero scan)
-- Published events older than 7 days are also swept by a weekly batch:
--   DELETE FROM reservation_events WHERE published_at IS NOT NULL
--     AND published_at < NOW() - INTERVAL '7 days'; (batched 1000 rows at a time)
DO $$
DECLARE
    d DATE := CURRENT_DATE;
BEGIN
    FOR i IN 0..29 LOOP
        EXECUTE format(
            'CREATE TABLE IF NOT EXISTS reservation_events_%s
             PARTITION OF reservation_events
             FOR VALUES FROM (%L) TO (%L)',
            to_char(d + i, 'YYYYMMDD'),
            d + i,
            d + i + 1
        );
    END LOOP;
END;
$$;
-- rollback SELECT 1; -- partitions are dropped individually by the runbook
```

**V006__create_expiry_state.sql**
```sql
-- liquibase formatted sql
-- changeset wirs:V006-create-expiry-state
CREATE TABLE reservation_expiry_state (
    id                      INT      PRIMARY KEY CHECK (id = 1),
    last_expiry_run         TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    processing_in_progress  BOOLEAN   NOT NULL DEFAULT FALSE
);
INSERT INTO reservation_expiry_state (id, last_expiry_run, processing_in_progress)
VALUES (1, CURRENT_TIMESTAMP, FALSE);
-- rollback DROP TABLE reservation_expiry_state;
```

**V007__seed_initial_products.sql**
```sql
-- liquibase formatted sql
-- changeset wirs:V007-seed-products
INSERT INTO products (sku, name, description) VALUES
    ('A100', 'Widget Alpha',   'Standard warehouse widget — category A'),
    ('B200', 'Bracket Beta',   'Mounting bracket for type-B assemblies'),
    ('C300', 'Connector Gamma','High-speed data connector, 3-port'),
    ('D400', 'Driver Delta',   'Precision driver set, 8-piece'),
    ('E500', 'Enclosure Echo', 'IP65 enclosure, 200x150x80mm');

INSERT INTO inventory (sku, total_stock, available_stock, reserved_stock) VALUES
    ('A100', 1000, 1000, 0),
    ('B200', 500,  500,  0),
    ('C300', 250,  250,  0),
    ('D400', 750,  750,  0),
    ('E500', 100,  100,  0);
-- rollback DELETE FROM inventory WHERE sku IN ('A100','B200','C300','D400','E500');
-- rollback DELETE FROM products WHERE sku IN ('A100','B200','C300','D400','E500');
```

---

### 2.3 TDD — Chunk 2 Tests

**Test class:** `LiquibaseMigrationIntegrationTest.java` (`@Tag("integration")`)

```
Test: allTablesExistAfterMigration
  - Inject DataSource. Execute: SELECT table_name FROM information_schema.tables
    WHERE table_schema = 'public'.
  - Assert: result contains all 6 table names: products, inventory, reservations,
    reservation_items, reservation_events, reservation_expiry_state.

Test: expiryCoordinationRowExists
  - Query: SELECT id FROM reservation_expiry_state WHERE id = 1.
  - Assert: exactly 1 row returned.

Test: seedDataPresent
  - Query: SELECT COUNT(*) FROM products.
  - Assert: count >= 5.
  - Query: SELECT COUNT(*) FROM inventory WHERE available_stock > 0.
  - Assert: count >= 5.
```

---

## Chunk 3 — Domain Model (Pure Java)

**Objective:** Implement the core domain aggregate classes (`Reservation`, `ReservationItem`,
`Inventory`, `ReservationFactory`) with zero framework dependencies.

**Dependencies:** Chunk 1 (project structure only — no runtime dependency).

**CRITICAL:** Run `grep -r "org.springframework" src/main/java/com/wirs/.../domain/` after
implementing. Zero results required before marking chunk done.

---

### 3.1 Package

All files live under `com.wirs.inventory.reservation.domain`.

### 3.2 ReservationItem (Value Object)

File: `domain/model/ReservationItem.java`

```
Record fields:
  - String sku        (non-null, non-blank)
  - long quantity     (positive)

Validation in compact constructor:
  - sku: Objects.requireNonNull, blank check via sku.isBlank()
  - quantity: must be > 0, throw IllegalArgumentException with message
    "Quantity must be positive for SKU: {sku}"
```

### 3.3 Inventory (Aggregate)

File: `domain/model/Inventory.java`

```
Fields (all final or updated in place):
  - String sku            (set at construction)
  - long totalStock       (set at construction)
  - long availableStock   (mutated by allocate/release)
  - long reservedStock    (mutated by allocate/release)
  - long version          (optimistic lock version — managed by JPA, not domain logic)

Methods:
  - allocate(long qty): void
      Precondition: availableStock >= qty; else throw InsufficientStockException(sku, qty, availableStock)
      Effect: availableStock -= qty; reservedStock += qty
      Postcondition: assert totalStock == availableStock + reservedStock (invariant check)

  - release(long qty): void
      Precondition: reservedStock >= qty; else throw IllegalStateException (programming error)
      Effect: availableStock += qty; reservedStock -= qty

  - hasAvailableStock(long qty): boolean
      Returns: availableStock >= qty

  - Private assertBalanceInvariant(): void
      Throws AssertionError if totalStock != availableStock + reservedStock.
      Called after every mutation. Documents the invariant as a runtime safety check.
```

### 3.4 Reservation (Aggregate Root)

File: `domain/model/Reservation.java`

```
Fields:
  - UUID id
  - String orderId
  - ReservationState state          (mutable — changes on transitions)
  - List<ReservationItem> items     (unmodifiable after construction)
  - Instant createdAt
  - Instant updatedAt               (mutable — updated on every state change)
  - Instant expiresAt

Methods:
  - confirm(): void
      Delegates to: this.state = state.confirm()
      Side effect: this.updatedAt = Instant.now() — do NOT use Instant.now() directly;
        pass the Clock via the factory. Post-transition, updatedAt should be set by the
        service/factory layer, NOT by the domain. The domain records that a transition occurred;
        the service records when.
      CORRECTION: updatedAt is set by the APPLICATION layer after calling confirm()/cancel(),
        not inside the domain method. The domain confirm() only returns/sets the new state.
        The service sets updatedAt on the entity before saving.

  - cancel(): void
      Delegates to: this.state = state.cancel()
      Same updatedAt note as above.

  - isExpired(Instant now): boolean
      Returns: now.isAfter(expiresAt)

  - status(): String
      Returns: state.name()

  - getState(): ReservationState  (package-private in domain, exposed via interface)

Constructor (package-private — use factory):
  All-args constructor. Items list is wrapped in Collections.unmodifiableList().
```

### 3.5 ReservationFactory

File: `domain/factory/ReservationFactory.java`

```
Fields:
  - Clock clock           (injected)
  - int expiryMinutes     (injected via @Value in application layer wiring)

Method: createPendingReservation(String orderId, List<ReservationItem> items): Reservation
  Validations (throw IllegalArgumentException):
    - orderId: null or blank
    - items: null or empty
    - any item: quantity <= 0 (though ReservationItem already validates this)

  Construction:
    - id = UUID.randomUUID()
    - createdAt = Instant.now(clock)
    - expiresAt = createdAt.plus(expiryMinutes, ChronoUnit.MINUTES)
    - state = new PendingState()
    - Return new Reservation(id, orderId, new PendingState(), List.copyOf(items),
        createdAt, createdAt, expiresAt)
```

---

### 3.6 TDD — Chunk 3 Tests

**Test class:** `ReservationItemTest.java` (`@Tag("unit")`)

```
Test: nullSku_throwsNullPointerException
Test: blankSku_throwsIllegalArgumentException
Test: zeroQuantity_throwsIllegalArgumentException
Test: negativeQuantity_throwsIllegalArgumentException
Test: validItem_constructsSuccessfully
```

**Test class:** `InventoryTest.java` (`@Tag("unit")`)

```
Test: allocate_reducesAvailableStockAndIncreasesReserved
Test: allocate_exactlyAvailableAmount_succeeds
Test: allocate_oneMoreThanAvailable_throwsInsufficientStockException
Test: release_restoresAvailableStock
Test: stockBalanceInvariant_holdsAfterAllocateAndRelease
  - Perform 5 allocations and 3 releases in sequence.
  - After each operation: assert totalStock == availableStock + reservedStock.
```

**Test class:** `ReservationFactoryTest.java` (`@Tag("unit")`)

```
Test: nullOrderId_throwsIllegalArgumentException
Test: emptyItems_throwsIllegalArgumentException
Test: validInput_createsReservationWithPendingState
Test: expiryTime_isExactlyNowPlusTtl
  - Use fixed Clock: Clock.fixed(Instant.parse("2026-05-01T10:00:00Z"), ZoneOffset.UTC)
  - Set expiryMinutes = 10
  - Assert: expiresAt == Instant.parse("2026-05-01T10:10:00Z")
Test: createdAt_matchesClockInstant
Test: itemsAreUnmodifiable
  - Assert: modifying returned items list throws UnsupportedOperationException
```

---

## Chunk 4 — Domain State Machine

**Objective:** Implement the state pattern classes. State transitions enforce the lifecycle contract.

**Dependencies:** Chunk 3 (Reservation, exceptions not yet created — use placeholder throws).

---

### 4.1 ReservationState (Abstract Class)

File: `domain/state/ReservationState.java`

```java
/** Contract for all reservation states. Each state defines its own valid transitions. */
public abstract class ReservationState {

    /**
     * Transitions to CONFIRMED state.
     *
     * @throws InvalidStateTransitionException if the transition is not valid from this state.
     */
    public abstract ReservationState confirm();

    /**
     * Transitions to CANCELLED state.
     *
     * @throws InvalidStateTransitionException if the transition is not valid from this state.
     */
    public abstract ReservationState cancel();

    /** Returns the string name of this state, matching the database CHECK constraint values. */
    public abstract String name();

    /**
     * Reconstructs a state instance from its persisted string name.
     *
     * @throws IllegalArgumentException if the status string does not match a known state.
     */
    public static ReservationState fromString(String status) {
        return switch (status) {
            case "PENDING"   -> new PendingState();
            case "CONFIRMED" -> new ConfirmedState();
            case "CANCELLED" -> new CancelledState();
            default -> throw new IllegalArgumentException("Unknown reservation status: " + status);
        };
    }
}
```

### 4.2 PendingState

File: `domain/state/PendingState.java`

```
confirm(): return new ConfirmedState()
cancel():  return new CancelledState()
name():    return "PENDING"
```

### 4.3 ConfirmedState

File: `domain/state/ConfirmedState.java`

```
confirm(): throw new InvalidStateTransitionException(
               "Cannot confirm a reservation that is already CONFIRMED")
cancel():  throw new InvalidStateTransitionException(
               "Cannot cancel a CONFIRMED reservation — stock is committed to fulfillment")
name():    return "CONFIRMED"
```

### 4.4 CancelledState

File: `domain/state/CancelledState.java`

```
confirm(): throw new InvalidStateTransitionException(
               "Cannot confirm a CANCELLED reservation")
cancel():  throw new InvalidStateTransitionException(
               "Cannot cancel a reservation that is already CANCELLED")
name():    return "CANCELLED"
```

---

### 4.5 TDD — Chunk 4 Tests

**Test class:** `PendingStateTest.java` (`@Tag("unit")`)

```
Test: confirm_returnConfirmedState
  - new PendingState().confirm() → instanceof ConfirmedState
Test: cancel_returnsCancelledState
  - new PendingState().cancel() → instanceof CancelledState
Test: name_returnsPending
```

**Test class:** `ConfirmedStateTest.java` (`@Tag("unit")`)

```
Test: confirm_throwsInvalidStateTransitionException
Test: cancel_throwsInvalidStateTransitionException
Test: name_returnsConfirmed
```

**Test class:** `CancelledStateTest.java` (`@Tag("unit")`)

```
Test: confirm_throwsInvalidStateTransitionException
Test: cancel_throwsInvalidStateTransitionException
Test: name_returnsCancelled
```

**Test class:** `ReservationStateTest.java` (`@Tag("unit")`)

```
Test: fromString_pending_returnsPendingState
Test: fromString_confirmed_returnsConfirmedState
Test: fromString_cancelled_returnsCancelledState
Test: fromString_unknownStatus_throwsIllegalArgumentException
```

---

## Chunk 5 — Domain Events & Exceptions

**Objective:** Define all domain events and domain exceptions. These form the contract between the
domain layer and the application layer.

**Dependencies:** Chunk 3 (domain model types for event fields).

---

### 5.1 Domain Event Interfaces

File: `application/event/DomainEvent.java` (lives in application layer — it is a port interface)

```java
/** Marker interface for all domain events emitted by this service. */
public interface DomainEvent {

    /** The reservation UUID that this event relates to. */
    UUID aggregateId();

    /** When the event occurred, in UTC. */
    Instant occurredAt();

    /** The string event type key (e.g., "RESERVATION_CREATED"). */
    String eventType();
}
```

### 5.2 Domain Event Records

All files in `domain/event/` — these are plain Java records.

**ReservationCreatedEvent.java:**
```
record fields: UUID reservationId, String orderId, List<ReservationItem> items, Instant expiresAt,
               Instant occurredAt
implements: DomainEvent
aggregateId(): returns reservationId
eventType():   returns "RESERVATION_CREATED"
```

**ReservationConfirmedEvent.java:**
```
record fields: UUID reservationId, String orderId, Instant occurredAt
implements: DomainEvent
eventType():   returns "RESERVATION_CONFIRMED"
```

**ReservationCancelledEvent.java:**
```
record fields: UUID reservationId, String orderId, String reason, Instant occurredAt
implements: DomainEvent
eventType():   returns "RESERVATION_CANCELLED"
reason values: "API" (client-initiated) or "TTL_EXPIRED" (expiry job)
```

### 5.3 Domain Exceptions

All files in `domain/exception/`.

**InsufficientStockException.java:**
```java
public class InsufficientStockException extends RuntimeException {
    /** Constructs with human-readable message including sku, requested, and available quantities. */
    public InsufficientStockException(String sku, long requested, long available) {
        super("SKU " + sku + " has only " + available + " units available; "
            + requested + " were requested");
    }
}
```

**InvalidStateTransitionException.java:**
```java
public class InvalidStateTransitionException extends RuntimeException {
    public InvalidStateTransitionException(String message) {
        super(message);
    }
}
```

**ReservationNotFoundException.java:**
```java
public class ReservationNotFoundException extends RuntimeException {
    public ReservationNotFoundException(UUID reservationId) {
        super("Reservation not found: " + reservationId);
    }
}
```

**SkuNotFoundException.java:**
```java
public class SkuNotFoundException extends RuntimeException {
    public SkuNotFoundException(String sku) {
        super("SKU not found: " + sku);
    }
}
```

**InventoryNotInitializedException.java:**
```java
/**
 * Thrown when a SKU exists in the product catalog but has no inventory record.
 * This is a data integrity failure, not a client error — maps to HTTP 500.
 */
public class InventoryNotInitializedException extends RuntimeException {
    public InventoryNotInitializedException(String sku) {
        super("Inventory record missing for SKU: " + sku
            + " — product exists but was never initialized in inventory");
    }
}
```

**DuplicateOrderException.java** (special: carries the existing reservation data):
```java
public class DuplicateOrderException extends RuntimeException {
    private final Reservation existingReservation;

    public DuplicateOrderException(Reservation existing) {
        super("Order already exists: " + existing.getOrderId());
        this.existingReservation = existing;
    }

    public Reservation getExistingReservation() {
        return existingReservation;
    }
}
```

---

### 5.4 TDD — Chunk 5 Tests

**Test class:** `DomainExceptionTest.java` (`@Tag("unit")`)

```
Test: insufficientStockException_messageContainsSkuAndQuantities
  - new InsufficientStockException("A100", 50, 30)
  - getMessage() contains "A100", "50", "30"

Test: inventoryNotInitializedException_messageContainsSku
  - new InventoryNotInitializedException("X999")
  - getMessage() contains "X999"

Test: reservationNotFoundException_messageContainsId
Test: duplicateOrderException_expiresExistingReservation
  - Assert: getExistingReservation() returns the same object passed to constructor
```

**Test class:** `ReservationCreatedEventTest.java` (`@Tag("unit")`)

```
Test: eventType_returnsReservationCreated
Test: aggregateId_returnsReservationId
```

---

## Chunk 6 — Infrastructure: JPA Entities

**Objective:** Map the six database tables to JPA `@Entity` classes. All entities live in the
infrastructure layer and have NO domain logic.

**Dependencies:** Chunk 2 (schema exists), Chunk 5 (exception types for validation context).

---

### 6.1 Package

All entities: `com.wirs.inventory.reservation.infrastructure.persistence.entity`

### 6.2 Entity Specifications

**ReservationEntity.java:**
```
@Entity @Table(name = "reservations")
Fields:
  @Id UUID id
  @Column(unique = true) String orderId
  @Column String status           — "PENDING" / "CONFIRMED" / "CANCELLED"
  @Column Instant createdAt
  @Column Instant updatedAt
  @Column Instant expiresAt
  @OneToMany(mappedBy = "reservation", cascade = CascadeType.ALL, orphanRemoval = true,
             fetch = FetchType.LAZY)
  List<ReservationItemEntity> items

  No @Version — pessimistic locking is used for state transitions.
  Use @PrePersist to set createdAt = updatedAt = Instant.now() if null.
  Use @PreUpdate to set updatedAt = Instant.now().
```

**ReservationItemEntity.java:**
```
@Entity @Table(name = "reservation_items")
Fields:
  @Id UUID id
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "reservation_id") ReservationEntity reservation
  @Column String sku
  @Column long quantity
```

**InventoryEntity.java:**
```
@Entity @Table(name = "inventory")
Fields:
  @Id String sku
  @Column long totalStock
  @Column long availableStock
  @Column long reservedStock
  @Version long version           — JPA optimistic lock version

  No business methods. Purely a data carrier.
```

**ProductEntity.java:**
```
@Entity @Table(name = "products")
Fields:
  @Id String sku
  @Column String name
  @Column String description
  @Column Instant createdAt
```

**ReservationEventEntity.java:**
```
@Entity @Table(name = "reservation_events")
Fields:
  @Id UUID id
  @Column UUID reservationId
  @Column String eventType
  @Column(columnDefinition = "jsonb") String payload    — serialized JSON
  @Column Instant createdAt
  @Column Instant publishedAt     — nullable; null until delivered

  @PrePersist: set id = UUID.randomUUID(), createdAt = Instant.now()
```

**ExpiryStateEntity.java:**
```
@Entity @Table(name = "reservation_expiry_state")
Fields:
  @Id int id                     — always 1
  @Column Instant lastExpiryRun
  @Column boolean processingInProgress
```

### 6.3 Column Naming Strategy

Configure in `application.yml`:
```yaml
spring:
  jpa:
    hibernate:
      naming:
        physical-strategy: org.hibernate.boot.model.naming.PhysicalNamingStrategyStandardImpl
```
Use explicit `@Column(name = "order_id")` annotations on every field. Do not rely on Hibernate
naming conventions — explicit names prevent surprises during database schema refactors.

---

### 6.4 TDD — Chunk 6 Tests

JPA entity tests are validated by the integration test suite (Chunk 16). For now, write:

**Test class:** `EntityMappingTest.java** (`@Tag("integration")`)

```
Test: reservationEntity_persistAndReload_fieldsMatch
  - Persist a ReservationEntity with two ReservationItemEntitys.
  - Reload by ID. Assert all field values match.
  - Assert items collection has 2 elements.

Test: inventoryEntity_versionIncrements_onUpdate
  - Persist InventoryEntity with version = 0.
  - Update availableStock. Save again.
  - Reload: assert version = 1.

Test: expiryStateEntity_singleRowConstraint
  - Attempt to insert a second row with id = 2.
  - Assert DataIntegrityViolationException is thrown.
```

---

## Chunk 7 — Infrastructure: JPA Repositories

**Objective:** Define all Spring Data JPA repository interfaces. Add custom query methods needed
by the application layer.

**Dependencies:** Chunk 6 (entities exist).

---

### 7.1 Repository Interfaces

Package: `com.wirs.inventory.reservation.infrastructure.persistence.repository`

**ReservationJpaRepository.java:**
```java
public interface ReservationJpaRepository extends JpaRepository<ReservationEntity, UUID> {

    /** Finds a reservation by its external order reference. */
    Optional<ReservationEntity> findByOrderId(String orderId);

    /** Returns a page of reservations, optionally filtered by status. */
    Page<ReservationEntity> findByStatus(String status, Pageable pageable);

    /**
     * Locks a reservation row for update (pessimistic write lock).
     * Used for confirm/cancel state transitions.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM ReservationEntity r WHERE r.id = :id")
    Optional<ReservationEntity> findByIdWithLock(@Param("id") UUID id);
}
```

**InventoryJpaRepository.java:**
```java
public interface InventoryJpaRepository extends JpaRepository<InventoryEntity, String> {

    /**
     * Finds inventory for a SKU and acquires a pessimistic write lock.
     * Not used for stock allocation (which uses optimistic locking) —
     * used only in the expiry job's stock release step.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM InventoryEntity i WHERE i.sku = :sku")
    Optional<InventoryEntity> findBySkuWithLock(@Param("sku") String sku);
}
```

**ReservationEventJpaRepository.java:**
```java
public interface ReservationEventJpaRepository
        extends JpaRepository<ReservationEventEntity, UUID> {

    /** Returns up to 50 unpublished events, oldest first. Used by the outbox relay. */
    List<ReservationEventEntity> findTop50ByPublishedAtIsNullOrderByCreatedAtAsc();
}
```

**ExpiryStateJpaRepository.java:**
```java
public interface ExpiryStateJpaRepository extends JpaRepository<ExpiryStateEntity, Integer> {

    /**
     * Attempts a non-blocking lock on the coordination row via SKIP LOCKED.
     * Returns empty if the row is already locked by another instance — callers skip immediately.
     * Do NOT use @Lock(PESSIMISTIC_WRITE): that generates blocking FOR UPDATE, causing all
     * non-winning instances to queue. SKIP LOCKED requires a native query.
     */
    @Query(
        value = "SELECT * FROM reservation_expiry_state WHERE id = 1 FOR UPDATE SKIP LOCKED",
        nativeQuery = true
    )
    Optional<ExpiryStateEntity> findCoordinationRowWithLock();
}
```

**Custom query for expiry — add to `ReservationJpaRepository`:**
```java
/** Returns PENDING reservations whose TTL has elapsed. Used by the expiry job. */
@Query("SELECT r FROM ReservationEntity r WHERE r.status = 'PENDING' AND r.expiresAt < :now")
List<ReservationEntity> findExpiredPendingReservations(@Param("now") Instant now);
```

---

### 7.2 TDD — Chunk 7 Tests

**Test class:** `ReservationJpaRepositoryTest.java** (`@Tag("integration")`)

```
Test: findByOrderId_existingOrder_returnsReservation
Test: findByOrderId_unknownOrder_returnsEmpty
Test: findByIdWithLock_acquiresLock (verify no exception; lock behavior tested via concurrent IT)
Test: findExpiredPendingReservations_returnOnlyExpired
  - Insert: R1 (PENDING, expiresAt = 1 hour ago), R2 (PENDING, expiresAt = 1 hour future),
            R3 (CONFIRMED, expiresAt = 1 hour ago)
  - Call with now = Instant.now()
  - Assert: only R1 returned
```

**Test class:** `InventoryJpaRepositoryTest.java** (`@Tag("integration")`)

```
Test: findBySku_existingSku_returnsInventory
Test: findBySku_unknownSku_returnsEmpty
```

---

## Chunk 8 — Infrastructure: Configuration Beans

**Objective:** Create all `@Configuration` classes. These are the wiring points for Spring beans
that infrastructure provides to the application layer.

**Dependencies:** Chunk 1 (application.yml values available).

---

### 8.1 Configuration Classes

Package: `com.wirs.inventory.reservation.infrastructure.config`

**RetryConfig.java:**
```java
@Configuration
@EnableRetry
public class RetryConfig {
    // @EnableRetry activates Spring Retry AOP proxy.
    // The @Retryable annotation on InventoryService.allocate() reads from this context.
    // No additional bean definitions required here — Spring Boot auto-configures the template.
}
```

**SchedulingConfig.java:**
```java
@Configuration
@EnableScheduling
public class SchedulingConfig {
    // @EnableScheduling activates @Scheduled method processing.
    // Dedicated thread pool prevents expiry job from blocking request handling threads.
    @Bean
    public TaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(2);
        scheduler.setThreadNamePrefix("expiry-job-");
        return scheduler;
    }
}
```

**SecurityConfig.java:**
```java
@Configuration
@EnableWebSecurity
public class SecurityConfig {
    // Registers ApiKeyAuthenticationFilter. Permits /health, /swagger-ui.html, /v3/api-docs/*.
    // All other paths require authentication via the filter.
    // CSRF disabled — stateless API with API key auth has no CSRF risk.
}
```

**OpenApiConfig.java:**
```java
@Configuration
public class OpenApiConfig {
    @Bean
    public OpenAPI inventoryReservationApi() {
        // Returns OpenAPI object with:
        //   title = "Warehouse Inventory Reservation API"
        //   version = "1.0.0"
        //   SecurityScheme "ApiKeyAuth" of type APIKEY in HEADER with name "X-API-Key"
    }
}
```

**ClockConfig.java:**
```java
@Configuration
public class ClockConfig {
    /** Provides the UTC system clock for production use. Tests override via @TestConfiguration. */
    @Bean
    public Clock utcClock() {
        return Clock.systemUTC();
    }
}
```

**ReservationExpiryConfig.java:**
```java
@ConfigurationProperties(prefix = "app.reservation")
public record ReservationExpiryConfig(int expiryMinutes) {}
```

Add `@EnableConfigurationProperties(ReservationExpiryConfig.class)` to the main application class
or to `RetryConfig.java`.

---

### 8.2 TDD — Chunk 8 Tests

```
Test class: RetryConfigTest (@Tag("unit"))
  - Assert @EnableRetry annotation is present on RetryConfig class via reflection.
  - This guards against accidentally removing the annotation.

Test class: SchedulingConfigTest (@Tag("unit"))
  - taskScheduler() returns a non-null bean.
  - Bean pool size is 2 (verify via cast to ThreadPoolTaskScheduler).
```

---

## Chunk 9 — Application: Event Publisher & Subscribers

**Objective:** Implement the in-process event fan-out mechanism and its two core subscribers
(audit log, structured log). This must be done BEFORE the service layer, as services depend on
`EventPublisher`.

**Dependencies:** Chunk 5 (DomainEvent interface and records), Chunk 6 (ReservationEventEntity),
Chunk 7 (ReservationEventJpaRepository).

---

### 9.1 Application Event Interfaces

Package: `com.wirs.inventory.reservation.application.event`

**EventPublisher.java (port interface):**
```java
/** Port: publishes a domain event to all registered subscribers. */
public interface EventPublisher {
    void publish(DomainEvent event);
}
```

**DomainEventSubscriber.java (port interface):**
```java
/** Port: receives domain events for processing. */
public interface DomainEventSubscriber {
    void on(DomainEvent event);
}
```

### 9.2 InProcessEventPublisher

```java
/** Fans out domain events to all registered DomainEventSubscriber implementations. */
@Component
public class InProcessEventPublisher implements EventPublisher {

    private final List<DomainEventSubscriber> subscribers;

    /** Spring injects all DomainEventSubscriber beans via List injection. */
    public InProcessEventPublisher(List<DomainEventSubscriber> subscribers) {
        this.subscribers = List.copyOf(subscribers);
    }

    @Override
    public void publish(DomainEvent event) {
        subscribers.forEach(subscriber -> subscriber.on(event));
    }
}
```

### 9.3 AuditEventSubscriber

Package: `application/event/subscriber/AuditEventSubscriber.java`

```
Implements: DomainEventSubscriber
Depends on: ReservationEventJpaRepository, ObjectMapper

on(DomainEvent event):
  1. Serialize event to JSON string via ObjectMapper.writeValueAsString(event)
  2. Build ReservationEventEntity:
       id           = UUID.randomUUID()
       reservationId = event.aggregateId()
       eventType    = abbreviateEventType(event.eventType())
                      ("RESERVATION_CREATED" → "CREATED", etc.)
       payload      = jsonString
       createdAt    = Instant.now()
       publishedAt  = null
  3. eventRepository.save(entity)

Private abbreviateEventType(String eventType): String
  switch: "RESERVATION_CREATED" → "CREATED"
          "RESERVATION_CONFIRMED" → "CONFIRMED"
          "RESERVATION_CANCELLED" → "CANCELLED"
          default → throw IllegalArgumentException
```

### 9.4 StructuredLogSubscriber

Package: `application/event/subscriber/StructuredLogSubscriber.java`

```
Implements: DomainEventSubscriber
Uses: SLF4J Logger + Logstash markers (net.logstash.logback.marker.Markers)

on(DomainEvent event):
  Emit ONE log.info() call with structured markers:
    - reservationId: event.aggregateId()
    - eventType: event.eventType()
    - orderId: extracted from concrete event type via instanceof pattern matching
    - occurredAt: event.occurredAt()
    - triggeredBy: "API" (hardcoded here; EXPIRY_JOB overrides via subtype field in events)

  For ReservationCancelledEvent: also log the "reason" field.
  Message: "Reservation state transition"

Pattern matching example:
  if (event instanceof ReservationCancelledEvent cancelled) {
      log.info("...", markers.with("reason", cancelled.reason()));
  }
```

---

### 9.5 TDD — Chunk 9 Tests

**Test class:** `InProcessEventPublisherTest.java` (`@Tag("unit")`)

```
Test: publish_fansOutToAllSubscribers
  - Mock two DomainEventSubscriber instances.
  - Construct InProcessEventPublisher with the two mocks.
  - Call publish(new ReservationCreatedEvent(...)).
  - Verify: each mock received exactly one on(event) call.

Test: publish_withNoSubscribers_completesWithoutException
  - Construct with empty list.
  - assertDoesNotThrow(() -> publisher.publish(event)).
```

**Test class:** `AuditEventSubscriberTest.java` (`@Tag("unit")`)

```
Mocks: ReservationEventJpaRepository, ObjectMapper

Test: on_reservationCreatedEvent_persistsWithCorrectEventType
  - Call on(new ReservationCreatedEvent(...))
  - Capture argument to eventRepository.save()
  - Assert: entity.getEventType() == "CREATED"
  - Assert: entity.getPublishedAt() == null

Test: on_reservationConfirmedEvent_persistsWithCorrectEventType
Test: on_reservationCancelledEvent_persistsWithCorrectEventType
```

---

## Chunk 10 — Application: InventoryService

**Objective:** Implement `InventoryService` with optimistic locking + retry (with jitter) for stock
allocation and pessimistic locking for stock release. SKU sort order is enforced via the
`SkuAllocationOrder` value object — not by convention inside the method.

**Dependencies:** Chunk 3 (Inventory domain model, ReservationItem), Chunk 6 (InventoryEntity),
Chunk 7 (repositories), Chunk 8 (RetryConfig enabling `@Retryable`).

---

### 10.0 SkuAllocationOrder

Package: `domain/model/SkuAllocationOrder.java`

```java
/**
 * Typed wrapper that guarantees SKUs are ordered alphabetically before inventory lock acquisition.
 * Enforces deadlock prevention at the type boundary — callers cannot pass an unsorted list.
 */
public record SkuAllocationOrder(List<ReservationItem> items) {

    /** Factory — sorts items by SKU ascending before constructing the record. */
    public static SkuAllocationOrder of(List<ReservationItem> unsorted) {
        return new SkuAllocationOrder(
            unsorted.stream()
                .sorted(Comparator.comparing(ReservationItem::sku))
                .toList()
        );
    }
}
```

> **Why a record and not a utility method?** A utility method `sortBySku(list)` returns a
> `List<ReservationItem>` — callers can ignore it or pass an un-sorted list elsewhere. A record
> with a private canonical constructor and a `static of()` factory makes it structurally impossible
> to create an out-of-order allocation order. The type system enforces what documentation cannot.

---

### 10.1 InventoryService

Package: `application/service/InventoryService.java`

```java
@Service
@Transactional
public class InventoryService {

    private final InventoryJpaRepository inventoryRepository;

    /**
     * Allocates stock for all items in one transactional operation, in SKU-sorted order.
     * Uses optimistic locking with jitter-randomized retry for each SKU.
     *
     * @param order pre-sorted allocation order produced by SkuAllocationOrder.of()
     * @throws InsufficientStockException if any SKU lacks available stock after retries.
     * @throws InventoryNotInitializedException if any SKU has no inventory record (data integrity failure).
     */
    @Retryable(
        retryFor = ObjectOptimisticLockingFailureException.class,
        maxAttempts = 3,
        backoff = @Backoff(delay = 100, multiplier = 2, maxDelay = 1000, random = true)
    )
    public void allocateStock(SkuAllocationOrder order) {
        for (ReservationItem item : order.items()) {
            // Missing inventory row is a data integrity failure (product never initialized),
            // not a client typo — throw InventoryNotInitializedException (→ HTTP 500 + alert).
            var entity = inventoryRepository.findById(item.sku())
                .orElseThrow(() -> new InventoryNotInitializedException(item.sku()));
            var inventory = toInventoryDomain(entity);
            inventory.allocate(item.quantity());     // throws InsufficientStockException
            updateInventoryEntity(entity, inventory);
            inventoryRepository.save(entity);
        }
    }

    /**
     * Releases reserved stock back to available. Called on cancel or expiry.
     *
     * @throws InventoryNotInitializedException if the SKU record has disappeared (data integrity).
     */
    public void releaseStock(List<ReservationItem> items) {
        for (ReservationItem item : items) {
            var entity = inventoryRepository.findBySkuWithLock(item.sku())
                .orElseThrow(() -> new InventoryNotInitializedException(item.sku()));
            var inventory = toInventoryDomain(entity);
            inventory.release(item.quantity());
            updateInventoryEntity(entity, inventory);
            inventoryRepository.save(entity);
        }
    }

    /**
     * Returns a snapshot of current stock levels for a SKU.
     *
     * @throws SkuNotFoundException if the SKU does not exist (404 — valid client lookup miss).
     */
    @Transactional(readOnly = true)
    public Inventory getInventory(String sku) {
        var entity = inventoryRepository.findById(sku)
            .orElseThrow(() -> new SkuNotFoundException(sku));
        return toInventoryDomain(entity);
    }

    // Private mapping methods — entity ↔ domain
    private Inventory toInventoryDomain(InventoryEntity entity) { ... }
    private void updateInventoryEntity(InventoryEntity entity, Inventory domain) { ... }
}
```

---

### 10.2 TDD — Chunk 10 Tests

**Test class:** `SkuAllocationOrderTest.java` (`@Tag("unit")`) — test first.

```
Test: of_unsortedList_returnsSortedBySkuAscending
  - Input: [("C300", 1), ("A100", 2), ("B200", 3)]
  - Assert order.items() == [("A100", 2), ("B200", 3), ("C300", 1)]

Test: of_alreadySortedList_returnsUnchangedOrder
  - Input: [("A100", 1), ("B200", 1)]
  - Assert items unchanged

Test: of_singleItem_returnsSingleItem
  - Input: [("Z999", 5)]
  - Assert items == [("Z999", 5)]
```

**Test class:** `InventoryServiceTest.java` (`@Tag("unit")`)

```
Mocks: InventoryJpaRepository

Setup: stubInventoryEntity("A100", total=100, available=100, reserved=0)

Test: allocateStock_sufficientStock_updatesAvailableAndReserved
  - Call allocateStock(SkuAllocationOrder.of([("A100", 30)]))
  - Capture saved entity: assert available=70, reserved=30

Test: allocateStock_insufficientStock_throwsInsufficientStockException
  - stubInventoryEntity("A100", total=100, available=20, reserved=80)
  - Call allocateStock(SkuAllocationOrder.of([("A100", 30)]))
  - Assert InsufficientStockException thrown

Test: allocateStock_skuNotFound_throwsInventoryNotInitializedException
  - when(repo.findById("UNKNOWN")).thenReturn(Optional.empty())
  - Assert InventoryNotInitializedException (not SkuNotFoundException — this is a 500, not 404)

Test: allocateStock_multiSku_processesInSkuSortedOrder
  - Stub A100 and B200.
  - Call allocateStock(SkuAllocationOrder.of([("B200", 1), ("A100", 1)])).
  - Capture findById() call order via InOrder mock.
  - Assert A100 was looked up before B200 — SkuAllocationOrder.of() sorted at construction time.

Test: releaseStock_releasesReservedStock
  - stubInventoryEntity("A100", total=100, available=70, reserved=30)
  - Call releaseStock([("A100", 30)])
  - Capture saved entity: assert available=100, reserved=0

Test: getInventory_returnsDomainObject
Test: getInventory_unknownSku_throwsSkuNotFoundException  ← stays 404 (client lookup)
```

---

## Chunk 11 — Application: ReservationService (Core)

**Objective:** Implement `ReservationService` with all three core operations: reserve, confirm,
cancel. This chunk includes the idempotency logic and defines `OrderedIdList`.

**Dependencies:** Chunk 3 (domain model), Chunk 5 (exceptions, events), Chunk 7 (repositories),
Chunk 9 (EventPublisher interface), Chunk 10 (InventoryService).

---

### 11.0 OrderedIdList

Package: `domain/model/OrderedIdList.java`

```java
/**
 * Typed wrapper that guarantees reservation IDs are UUID-sorted before any batch lock acquisition.
 * Required by any repository method that touches more than one reservation row via SELECT FOR UPDATE.
 * Same pattern as SkuAllocationOrder — the type enforces what documentation cannot.
 */
public record OrderedIdList(List<UUID> ids) {

    /** Factory — sorts IDs by natural UUID order (lexicographic) before constructing the record. */
    public static OrderedIdList of(List<UUID> unsorted) {
        return new OrderedIdList(
            unsorted.stream()
                .sorted()
                .toList()
        );
    }
}
```

> **Rule:** Any future repository method that acquires locks on multiple reservation rows (e.g., bulk
> confirm, bulk cancel) **must** accept `OrderedIdList`, not a raw `List<UUID>`. This is enforced at
> code review. The type is defined here so the pattern exists before any bulk endpoint is written.

---

### 11.1 ReservationService

Package: `application/service/ReservationService.java`

```java
@Service
@Transactional
public class ReservationService {

    private final ReservationJpaRepository reservationRepository;
    private final InventoryService inventoryService;
    private final ReservationFactory reservationFactory;
    private final EventPublisher eventPublisher;
    private final Clock clock;

    /**
     * Creates a new PENDING reservation and allocates stock.
     * Returns the existing reservation if orderId already exists (idempotent).
     *
     * @throws InsufficientStockException if any SKU lacks available stock.
     * @throws SkuNotFoundException if any SKU does not exist.
     * @throws DuplicateOrderException if orderId already exists (mapped to HTTP 200).
     */
    public Reservation reserve(String orderId, List<ReservationItem> items) {
        // Application-layer idempotency check (fast path for sequential duplicates)
        var existing = reservationRepository.findByOrderId(orderId);
        if (existing.isPresent()) {
            throw new DuplicateOrderException(toDomain(existing.get()));
        }

        var reservation = reservationFactory.createPendingReservation(orderId, items);
        inventoryService.allocateStock(SkuAllocationOrder.of(items)); // may throw InsufficientStockException
        var entity = toEntity(reservation);

        try {
            var saved = reservationRepository.save(entity);
            var domain = toDomain(saved);
            eventPublisher.publish(
                new ReservationCreatedEvent(
                    domain.getId(), domain.getOrderId(), domain.getItems(),
                    domain.getExpiresAt(), Instant.now(clock)));
            return domain;
        } catch (DataIntegrityViolationException e) {
            // Concurrent duplicate: UNIQUE constraint violation on order_id.
            // Re-read the winning reservation and return it as if it were a duplicate.
            var winner = reservationRepository.findByOrderId(orderId)
                .orElseThrow(() -> new IllegalStateException(
                    "Unique constraint violated but reservation not found: " + orderId));
            throw new DuplicateOrderException(toDomain(winner));
        }
    }

    /**
     * Transitions a PENDING reservation to CONFIRMED.
     *
     * @throws ReservationNotFoundException if the reservation does not exist.
     * @throws InvalidStateTransitionException if the reservation is not PENDING.
     */
    public Reservation confirm(UUID reservationId) {
        var entity = reservationRepository.findByIdWithLock(reservationId)
            .orElseThrow(() -> new ReservationNotFoundException(reservationId));
        var reservation = toDomain(entity);

        reservation.confirm();    // delegates to state machine; throws if invalid

        entity.setStatus(reservation.status());
        entity.setUpdatedAt(Instant.now(clock));
        var saved = reservationRepository.save(entity);
        var domain = toDomain(saved);

        eventPublisher.publish(
            new ReservationConfirmedEvent(domain.getId(), domain.getOrderId(), Instant.now(clock)));
        return domain;
    }

    /**
     * Transitions a PENDING reservation to CANCELLED and releases held stock.
     *
     * @throws ReservationNotFoundException if the reservation does not exist.
     * @throws InvalidStateTransitionException if the reservation is not PENDING.
     */
    public Reservation cancel(UUID reservationId) {
        var entity = reservationRepository.findByIdWithLock(reservationId)
            .orElseThrow(() -> new ReservationNotFoundException(reservationId));
        var reservation = toDomain(entity);

        reservation.cancel();    // throws if invalid

        inventoryService.releaseStock(reservation.getItems());
        entity.setStatus(reservation.status());
        entity.setUpdatedAt(Instant.now(clock));
        var saved = reservationRepository.save(entity);
        var domain = toDomain(saved);

        eventPublisher.publish(
            new ReservationCancelledEvent(
                domain.getId(), domain.getOrderId(), "API", Instant.now(clock)));
        return domain;
    }

    /** Returns a reservation by ID. */
    @Transactional(readOnly = true)
    public Reservation findById(UUID reservationId) {
        return reservationRepository.findById(reservationId)
            .map(this::toDomain)
            .orElseThrow(() -> new ReservationNotFoundException(reservationId));
    }

    /** Returns a paginated list of reservations, optionally filtered by status. */
    @Transactional(readOnly = true)
    public Page<Reservation> findAll(@Nullable String status, Pageable pageable) {
        if (status != null) {
            return reservationRepository.findByStatus(status, pageable).map(this::toDomain);
        }
        return reservationRepository.findAll(pageable).map(this::toDomain);
    }

    // Private entity ↔ domain mapping methods
    private Reservation toDomain(ReservationEntity entity) { ... }
    private ReservationEntity toEntity(Reservation domain) { ... }
}
```

---

### 11.2 TDD — Chunk 11 Tests

**Test class:** `ReservationServiceTest.java` (`@Tag("unit")`)

```
Mocks: ReservationJpaRepository, InventoryService, ReservationFactory, EventPublisher, Clock

Test: reserve_newOrder_createsReservationAndPublishesEvent
  - when(repo.findByOrderId("ORD-1")).thenReturn(Optional.empty())
  - when(factory.createPendingReservation(...)).thenReturn(pendingReservation)
  - when(repo.save(...)).thenReturn(savedEntity)
  - Call reserve("ORD-1", items)
  - verify(inventoryService).allocateStock(any(SkuAllocationOrder.class))
  - verify(eventPublisher).publish(instanceOf(ReservationCreatedEvent.class))

Test: reserve_duplicateOrderId_throwsDuplicateOrderException
  - when(repo.findByOrderId("ORD-1")).thenReturn(Optional.of(existingEntity))
  - Assert DuplicateOrderException thrown
  - verify(inventoryService, never()).allocateStock(any())

Test: confirm_pendingReservation_transitionsToConfirmedAndPublishesEvent
  - Setup: entity with status="PENDING"
  - when(repo.findByIdWithLock(id)).thenReturn(Optional.of(entity))
  - Call confirm(id)
  - Capture saved entity: assert status == "CONFIRMED"
  - verify(eventPublisher).publish(instanceOf(ReservationConfirmedEvent.class))

Test: confirm_nonExistent_throwsReservationNotFoundException
  - when(repo.findByIdWithLock(id)).thenReturn(Optional.empty())
  - Assert ReservationNotFoundException

Test: confirm_alreadyConfirmed_throwsInvalidStateTransitionException
  - Setup: entity with status="CONFIRMED"
  - Assert InvalidStateTransitionException

Test: cancel_pendingReservation_releasesStockAndPublishesEvent
  - Setup: entity with status="PENDING", items=[("A100", 30)]
  - Call cancel(id)
  - verify(inventoryService).releaseStock(items with A100=30)
  - verify(eventPublisher).publish(instanceOf(ReservationCancelledEvent.class))
  - Verify event reason == "API"

Test: cancel_confirmedReservation_throwsInvalidStateTransitionException
Test: cancel_nonExistent_throwsReservationNotFoundException
Test: findById_nonExistent_throwsReservationNotFoundException
Test: reserve_stockAllocationFails_reservationNotPersisted
  - when(inventoryService.allocateStock(...)).thenThrow(InsufficientStockException)
  - verify(repo, never()).save(any())
```

---

## Chunk 12 — Application: ReservationExpiryJob

**Objective:** Implement the scheduled expiry job with database-coordinated single-instance
execution using `SELECT FOR UPDATE SKIP LOCKED` (non-blocking) and stuck-flag override.
Use `@Scheduled(fixedDelay = 120_000)` to prevent synchronized multi-instance wakeups.

**Dependencies:** Chunk 7 (ExpiryStateJpaRepository, ReservationJpaRepository), Chunk 10
(InventoryService for stock release), Chunk 5 (events for cancellation).

---

### 12.1 ReservationExpiryJob

Package: `application/job/ReservationExpiryJob.java`

```java
/**
 * Scheduled job that cancels PENDING reservations whose TTL has elapsed.
 * Coordinates across multiple service instances via a single database row with pessimistic locking.
 */
@Component
public class ReservationExpiryJob {

    private static final int STUCK_FLAG_TIMEOUT_MINUTES = 5;

    private final ExpiryStateJpaRepository expiryStateRepository;
    private final ReservationJpaRepository reservationRepository;
    private final InventoryService inventoryService;
    private final ReservationEventJpaRepository eventRepository;
    private final EventPublisher eventPublisher;
    private final Clock clock;

    /**
     * Runs with a 2-minute fixed delay after each completion. SKIP LOCKED on the coordination row
     * means non-winning instances return immediately rather than queuing.
     */
    @Scheduled(fixedDelay = 120_000)
    @Transactional
    public void expireReservations() {
        // SKIP LOCKED: if the row is locked by another instance, this returns empty immediately.
        // No blocking, no thread or connection waste on the losing instances.
        var coordinationRow = expiryStateRepository.findCoordinationRowWithLock();
        if (coordinationRow.isEmpty()) {
            log.debug("Expiry job skipped — coordination row locked by another instance");
            return;
        }

        // Skip if another instance is processing, unless the flag is stale (crashed instance).
        if (isBlockedByActiveJob(coordinationRow.get())) {
            log.info("Expiry job skipped — another instance is actively processing");
            return;
        }

        var row = coordinationRow.get();
        row.setProcessingInProgress(true);
        row.setLastExpiryRun(Instant.now(clock));
        expiryStateRepository.save(row);

        try {
            processExpiredReservations();
        } finally {
            row.setProcessingInProgress(false);
            expiryStateRepository.save(row);
        }
    }

    /** Public to allow direct invocation in integration tests. */
    @Transactional
    public void processExpiredReservations() {
        var expiredEntities = reservationRepository
            .findExpiredPendingReservations(Instant.now(clock));

        for (var entity : expiredEntities) {
            try {
                expireSingleReservation(entity);
            } catch (Exception e) {
                log.warn("Failed to expire reservation id={}: {}", entity.getId(), e.getMessage());
                // Continue processing remaining reservations — do not abort the batch.
            }
        }
    }

    private void expireSingleReservation(ReservationEntity entity) {
        // Acquire pessimistic lock on the reservation to prevent race with concurrent confirm/cancel.
        var locked = reservationRepository.findByIdWithLock(entity.getId());
        if (locked.isEmpty() || !"PENDING".equals(locked.get().getStatus())) {
            return;    // Already transitioned by a concurrent API call — nothing to do.
        }
        var reservation = toDomain(locked.get());
        reservation.cancel();    // always valid from PENDING

        var items = reservation.getItems();
        inventoryService.releaseStock(items);

        locked.get().setStatus("CANCELLED");
        locked.get().setUpdatedAt(Instant.now(clock));
        reservationRepository.save(locked.get());

        eventPublisher.publish(
            new ReservationCancelledEvent(
                reservation.getId(), reservation.getOrderId(), "TTL_EXPIRED", Instant.now(clock)));
    }

    private boolean isBlockedByActiveJob(ExpiryStateEntity state) {
        if (!state.isProcessingInProgress()) {
            return false;
        }
        // Override stale flag: previous instance crashed more than STUCK_FLAG_TIMEOUT_MINUTES ago.
        var stuckThreshold = Instant.now(clock)
            .minus(STUCK_FLAG_TIMEOUT_MINUTES, ChronoUnit.MINUTES);
        return state.getLastExpiryRun().isAfter(stuckThreshold);
    }

    private Reservation toDomain(ReservationEntity entity) { ... }
}
```

---

### 12.2 TDD — Chunk 12 Tests

**Test class:** `ReservationExpiryJobTest.java` (`@Tag("unit")`)

```
Mocks: ExpiryStateJpaRepository, ReservationJpaRepository, InventoryService,
       ReservationEventJpaRepository, EventPublisher, Clock

Test: expireReservations_processingInProgress_recentRun_skipsExecution
  - Stub coordination row: processingInProgress=true, lastExpiryRun=2 minutes ago
  - Call expireReservations()
  - verify(reservationRepository, never()).findExpiredPendingReservations(any())

Test: expireReservations_processingInProgress_staleRun_overridesAndProcesses
  - Stub coordination row: processingInProgress=true, lastExpiryRun=10 minutes ago
  - Stub findExpiredPendingReservations to return empty list
  - Call expireReservations()
  - Verify: processingInProgress was set to true (override), then set to false (finally block)

Test: expireReservations_expiredReservation_cancelledAndStockReleased
  - Stub coordination row: processingInProgress=false
  - Stub findExpiredPendingReservations to return [entity with PENDING, items=[A100:30]]
  - Stub findByIdWithLock to return same entity
  - Call expireReservations()
  - verify(inventoryService).releaseStock([A100:30])
  - Capture saved entity: assert status="CANCELLED"
  - verify(eventPublisher).publish(event where reason="TTL_EXPIRED")

Test: expireReservations_reservationAlreadyConfirmed_skipsGracefully
  - Stub findByIdWithLock to return entity with status="CONFIRMED"
  - Call expireReservations()
  - verify(inventoryService, never()).releaseStock(any())

Test: expireReservations_singleReservationFails_continuesOtherReservations
  - Stub two expired reservations: R1 and R2
  - Stub findByIdWithLock for R1 to throw RuntimeException
  - Call expireReservations()
  - Verify: R2 is still processed (inventoryService.releaseStock called once for R2)

Test: expireReservations_processingFlagClearedInFinallyBlock
  - Stub to return one expired reservation. findByIdWithLock throws RuntimeException.
  - Call expireReservations()
  - verify: coordination row setProcessingInProgress(false) was called despite exception
```

---

## Chunk 12b — Application: Core Outbox Relay

**Objective:** Implement `ReservationEventRelay` — a `@Scheduled` poller that re-dispatches events
with `published_at = NULL`. This makes the outbox a genuine guaranteed-delivery mechanism for the
core track, not just an audit log. Without this relay, events missed by `@TransactionalEventListener`
(JVM crash between commit and dispatch) are permanently lost.

**Dependencies:** Chunk 7 (ReservationEventJpaRepository), Chunk 9 (EventPublisher,
InProcessEventPublisher), Chunk 5 (domain events). Must be placed after Chunk 12 — relies on
`reservation_events` being fully populated by the service and expiry job.

---

### 12b.1 ReservationEventRelay

Package: `application/relay/ReservationEventRelay.java`

```java
/**
 * Polls reservation_events for undelivered events and dispatches them to in-process subscribers.
 * Acts as the durability backstop for the Transactional Outbox pattern.
 */
@Component
@Slf4j
public class ReservationEventRelay {

    private static final int BATCH_SIZE = 50;

    private final ReservationEventJpaRepository eventRepository;
    private final EventPublisher eventPublisher;
    private final ObjectMapper objectMapper;

    /**
     * Runs every 30 seconds. Finds up to 50 oldest undelivered events, dispatches each,
     * and marks published_at on success. On subscriber failure, logs and continues — the
     * event remains published_at = NULL and will be retried on the next poll.
     */
    @Scheduled(fixedDelay = 30_000)
    @Transactional
    public void relayUnpublishedEvents() {
        var unpublished = eventRepository
            .findTopNByPublishedAtIsNullOrderByCreatedAtAsc(BATCH_SIZE);

        if (unpublished.isEmpty()) {
            return;
        }

        log.debug("Relay found {} undelivered event(s)", unpublished.size());

        for (var eventEntity : unpublished) {
            try {
                var domainEvent = deserialize(eventEntity);
                eventPublisher.publish(domainEvent);
                eventEntity.setPublishedAt(Instant.now());
                eventRepository.save(eventEntity);
            } catch (Exception e) {
                // Log and continue — this event stays NULL and is retried next cycle.
                log.warn("Relay failed for eventId={}, type={}: {}",
                    eventEntity.getId(), eventEntity.getEventType(), e.getMessage());
            }
        }
    }

    private DomainEvent deserialize(ReservationEventEntity entity) throws IOException {
        // Deserialize JSONB payload back to the correct event type.
        return switch (entity.getEventType()) {
            case "CREATED"   -> objectMapper.readValue(entity.getPayload(), ReservationCreatedEvent.class);
            case "CONFIRMED" -> objectMapper.readValue(entity.getPayload(), ReservationConfirmedEvent.class);
            case "CANCELLED" -> objectMapper.readValue(entity.getPayload(), ReservationCancelledEvent.class);
            default -> throw new IllegalArgumentException("Unknown event type: " + entity.getEventType());
        };
    }
}
```

**Repository method to add to `ReservationEventJpaRepository`:**

```java
/**
 * Returns up to limit undelivered events, oldest first.
 * The partial index on (created_at) WHERE published_at IS NULL makes this query efficient.
 */
@Query("SELECT e FROM ReservationEventEntity e WHERE e.publishedAt IS NULL ORDER BY e.createdAt ASC LIMIT :limit")
List<ReservationEventEntity> findTopNByPublishedAtIsNullOrderByCreatedAtAsc(@Param("limit") int limit);
```

**Package addition — update the package structure listing in Chunk 9:**

```
application/
└── relay/
    └── ReservationEventRelay.java    @Component — polls and dispatches unpublished events
```

---

### 12b.2 TDD — Chunk 12b Tests

**Test class:** `ReservationEventRelayTest.java` (`@Tag("unit")`)

```
Mocks: ReservationEventJpaRepository, EventPublisher, ObjectMapper

Test: relayUnpublishedEvents_noUnpublishedEvents_doesNothing
  - stub findTopN... returns empty list
  - verify: eventPublisher.publish never called
  - verify: eventRepository.save never called

Test: relayUnpublishedEvents_oneEvent_dispatchesAndMarksPublished
  - stub findTopN... returns [createdEventEntity]
  - stub objectMapper.readValue returns ReservationCreatedEvent
  - Call relayUnpublishedEvents()
  - verify: eventPublisher.publish called once with the event
  - verify: eventRepository.save called with entity where publishedAt != null

Test: relayUnpublishedEvents_publisherThrows_logsAndContinues
  - stub findTopN... returns [entity1, entity2]
  - eventPublisher.publish throws RuntimeException on entity1, succeeds on entity2
  - Call relayUnpublishedEvents()
  - verify: eventRepository.save called once (entity2 only — entity1 stayed NULL)
  - verify: no exception propagated from relayUnpublishedEvents()

Test: relayUnpublishedEvents_unknownEventType_logsAndContinues
  - stub entity with eventType = "UNKNOWN"
  - Call relayUnpublishedEvents()
  - verify: eventRepository.save not called for that entity
  - verify: no exception propagated
```

---

## Chunk 13 — API: DTOs & Validation

**Objective:** Define all request/response DTOs with Bean Validation constraints. No Spring MVC
logic in this chunk — DTOs only.

**Dependencies:** Chunk 5 (domain event types inform DTO shapes).

---

### 13.1 Request DTOs

Package: `api/dto/request/`

**ReserveItemRequest.java:**
```java
/** Line item in a reservation request: one SKU and quantity. */
public record ReserveItemRequest(
    @NotBlank(message = "SKU must not be blank") String sku,
    @NotNull @Min(value = 1, message = "Quantity must be at least 1") Long quantity
) {}
```

**ReserveRequest.java:**
```java
/** Request body for POST /api/v1/reservations. */
public record ReserveRequest(
    @NotBlank(message = "Order ID must not be blank") String orderId,
    @NotEmpty(message = "Items list must not be empty")
    @Valid List<ReserveItemRequest> items
) {}
```

### 13.2 Response DTOs

Package: `api/dto/response/`

**ApiError.java:**
```java
/** Error descriptor embedded in all API responses when an error occurs. */
public record ApiError(String code, String message) {}
```

**ApiResponse.java:**
```java
/** Universal response envelope. Exactly one of data or error is non-null. */
public record ApiResponse<T>(T data, ApiError error) {
    /** Factory: success response with data and null error. */
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(data, null);
    }
    /** Factory: error response with null data and structured error. */
    public static <T> ApiResponse<T> error(String code, String message) {
        return new ApiResponse<>(null, new ApiError(code, message));
    }
}
```

**ReservationItemResponse.java:**
```java
public record ReservationItemResponse(String sku, long quantity) {}
```

**ReservationResponse.java:**
```java
public record ReservationResponse(
    UUID id,
    String orderId,
    String status,
    List<ReservationItemResponse> items,
    Instant createdAt,
    Instant updatedAt,
    Instant expiresAt
) {}
```

**InventoryResponse.java:**
```java
public record InventoryResponse(
    String sku,
    long totalStock,
    long availableStock,
    long reservedStock
) {}
```

**PagedResponse.java:**
```java
/** Pagination wrapper for list endpoints. */
public record PagedResponse<T>(
    List<T> content,
    int page,
    int size,
    long totalElements,
    int totalPages
) {
    /** Constructs from a Spring Data Page. */
    public static <T> PagedResponse<T> from(Page<T> page) {
        return new PagedResponse<>(
            page.getContent(),
            page.getNumber(),
            page.getSize(),
            page.getTotalElements(),
            page.getTotalPages()
        );
    }
}
```

---

### 13.3 TDD — Chunk 13 Tests

**Test class:** `ReserveRequestValidationTest.java** (`@Tag("unit")`)

Uses: `jakarta.validation.Validator` (no Spring context needed)

```
Setup: Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

Test: validRequest_noViolations
Test: nullOrderId_producesViolation
Test: blankOrderId_producesViolation
Test: emptyItems_producesViolation
Test: itemWithZeroQuantity_producesViolation
Test: itemWithNegativeQuantity_producesViolation
Test: itemWithBlankSku_producesViolation
```

---

## Chunk 14 — API: Controllers & Exception Handler

**Objective:** Implement REST controllers and the centralized exception handler.

**Dependencies:** Chunk 11 (ReservationService), Chunk 10 (InventoryService), Chunk 13 (DTOs).

---

### 14.1 ReservationController

Package: `api/controller/ReservationController.java`

```
@RestController @RequestMapping("/api/v1/reservations")

Endpoints:
  POST /
    @RequestBody @Valid ReserveRequest
    → reservationService.reserve(req.orderId(), toItems(req.items()))
    → HTTP 201 with ApiResponse.success(toResponse(reservation))
    Exception handling: none — delegates to GlobalExceptionHandler

  POST /{id}/confirm
    @PathVariable UUID id
    → reservationService.confirm(id)
    → HTTP 200 with ApiResponse.success(toResponse(reservation))

  POST /{id}/cancel
    @PathVariable UUID id
    → reservationService.cancel(id)
    → HTTP 200 with ApiResponse.success(toResponse(reservation))

  GET /{id}
    @PathVariable UUID id
    → reservationService.findById(id)
    → HTTP 200 with ApiResponse.success(toResponse(reservation))

  GET /
    @RequestParam(required = true) int page
    @RequestParam(required = true) @Min(1) @Max(100) int size
    @RequestParam(required = false) ReservationStatus status
    → PageRequest.of(page, size)
    → reservationService.findAll(status?.name(), pageable)
    → HTTP 200 with ApiResponse.success(PagedResponse.from(page.map(this::toResponse)))

Private mapping: toItems(List<ReserveItemRequest>), toResponse(Reservation)
```

### 14.2 InventoryController

```
@RestController @RequestMapping("/api/v1/inventory")

GET /{sku}
  @PathVariable String sku
  → inventoryService.getInventory(sku)
  → HTTP 200 with ApiResponse.success(toInventoryResponse(inventory))
```

### 14.3 HealthController

```
@RestController (public — no auth filter)

GET /health
  → HTTP 200 with { "status": "UP", "database": "UP" }
  Check DB: try simple SELECT 1 via DataSource; catch → "database": "DOWN", return HTTP 503
```

### 14.4 GlobalExceptionHandler

Package: `api/exception/GlobalExceptionHandler.java`

```java
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    private final MeterRegistry meterRegistry;

    public GlobalExceptionHandler(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    /** Maps InsufficientStockException → 409 CONFLICT. */
    @ExceptionHandler(InsufficientStockException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ApiResponse<Void> handleInsufficientStock(InsufficientStockException ex) {
        return ApiResponse.error("INSUFFICIENT_STOCK", ex.getMessage());
    }

    /** Maps ReservationNotFoundException → 404 NOT FOUND. */
    @ExceptionHandler(ReservationNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiResponse<Void> handleNotFound(ReservationNotFoundException ex) { ... }

    /** Maps SkuNotFoundException → 404 NOT FOUND. */
    @ExceptionHandler(SkuNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiResponse<Void> handleSkuNotFound(SkuNotFoundException ex) { ... }

    /**
     * Maps InventoryNotInitializedException → 500 INTERNAL_SERVER_ERROR.
     * This is a data integrity failure: a product exists but was never initialized in inventory.
     * Increments a Micrometer counter so the alert fires immediately.
     */
    @ExceptionHandler(InventoryNotInitializedException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiResponse<Void> handleInventoryNotInitialized(InventoryNotInitializedException ex) {
        meterRegistry.counter("inventory.not_initialized.count",
            "sku", extractSku(ex.getMessage())).increment();
        log.error("CRITICAL — inventory not initialized: {}", ex.getMessage());
        return ApiResponse.error("INVENTORY_NOT_INITIALIZED", ex.getMessage());
    }

    /** Maps InvalidStateTransitionException → 409 CONFLICT. */
    @ExceptionHandler(InvalidStateTransitionException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ApiResponse<Void> handleInvalidTransition(InvalidStateTransitionException ex) { ... }

    /**
     * Maps DuplicateOrderException → HTTP 200.
     * Returns the existing reservation in the data field — this is a success path, not an error.
     */
    @ExceptionHandler(DuplicateOrderException.class)
    @ResponseStatus(HttpStatus.OK)
    public ApiResponse<ReservationResponse> handleDuplicateOrder(DuplicateOrderException ex) {
        return ApiResponse.success(toReservationResponse(ex.getExistingReservation()));
    }

    /** Maps @Valid constraint violations → 400 BAD REQUEST. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleValidationErrors(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
            .map(e -> e.getField() + ": " + e.getDefaultMessage())
            .collect(Collectors.joining("; "));
        return ApiResponse.error("INVALID_REQUEST", message);
    }

    /** Maps MethodArgumentTypeMismatchException (bad enum value) → 400. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleTypeMismatch(MethodArgumentTypeMismatchException ex) { ... }

    /** Maps missing required parameters → 400. */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleMissingParam(MissingServletRequestParameterException ex) { ... }
}
```

---

### 14.5 TDD — Chunk 14 Tests

**Test class:** `GlobalExceptionHandlerTest.java** (`@Tag("unit")`)

Uses: `@WebMvcTest(ReservationController.class)` + mocked service

```
Test: insufficientStock_returns409WithCode
  - Mock reservationService.reserve() to throw InsufficientStockException("A100", 50, 30)
  - POST /api/v1/reservations with valid body
  - Assert: HTTP 409, response body $.error.code == "INSUFFICIENT_STOCK"
  - Assert: response body $.error.message contains "A100"

Test: duplicateOrder_returns200WithExistingReservation
  - Mock to throw DuplicateOrderException(existingReservation)
  - Assert: HTTP 200, $.data.reservationId matches existingReservation.id
  - Assert: $.error is null

Test: reservationNotFound_returns404
Test: invalidStateTransition_returns409
Test: missingOrderId_returns400WithInvalidRequestCode
Test: invalidStatusFilter_returns400
Test: missingPaginationParams_returns400

Test: inventoryNotInitialized_returns500WithCodeAndIncrementsCounter
  - Mock reservationService.reserve() to throw InventoryNotInitializedException("X999")
  - Assert: HTTP 500, $.error.code == "INVENTORY_NOT_INITIALIZED"
  - Assert: meterRegistry counter "inventory.not_initialized.count" was incremented
```

---

## Chunk 15 — API: Security Filter & Configuration

**Objective:** Implement API key authentication. All endpoints except `/health`, `/swagger-ui.html`,
and `/v3/api-docs/**` require a valid `X-API-Key` header.

**Dependencies:** Chunk 8 (SecurityConfig skeleton), Chunk 1 (application.yml with `app.security.api-keys`).

---

### 15.1 ApiKeyAuthenticationFilter

Package: `api/security/ApiKeyAuthenticationFilter.java`

```java
/** Validates the X-API-Key header on every protected request. */
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    private static final String API_KEY_HEADER = "X-API-Key";

    private final Set<String> validApiKeys;

    /**
     * @param apiKeysRaw Comma-separated list of valid API keys from application configuration.
     */
    public ApiKeyAuthenticationFilter(@Value("${app.security.api-keys}") String apiKeysRaw) {
        this.validApiKeys = Set.of(apiKeysRaw.split(","));
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {

        String key = request.getHeader(API_KEY_HEADER);

        if (key == null || !validApiKeys.contains(key.trim())) {
            writeUnauthorizedResponse(response);
            return;
        }

        SecurityContextHolder.getContext().setAuthentication(
            new PreAuthenticatedAuthenticationToken(key, null, List.of()));
        chain.doFilter(request, response);
    }

    private void writeUnauthorizedResponse(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(
            "{\"data\":null,\"error\":{\"code\":\"UNAUTHORIZED\","
            + "\"message\":\"Missing or invalid X-API-Key header\"}}");
    }
}
```

### 15.2 SecurityConfig (complete implementation)

```
Permitted paths (no authentication): /health, /swagger-ui.html, /swagger-ui/**, /v3/api-docs/**
All other paths: require authentication (filter sets SecurityContext)

CSRF: disabled (stateless API)
Session: STATELESS (no HttpSession)
Filter: ApiKeyAuthenticationFilter registered before UsernamePasswordAuthenticationFilter
```

---

### 15.3 TDD — Chunk 15 Tests

**Test class:** `ApiKeyAuthenticationFilterTest.java** (`@Tag("unit")`)

Uses: `MockFilterChain`, `MockHttpServletRequest`, `MockHttpServletResponse`

```
Test: missingApiKey_returns401WithUnauthorizedCode
  - No X-API-Key header on request
  - Assert response status 401
  - Assert response body contains "UNAUTHORIZED"
  - Assert chain.doFilter was NOT called

Test: invalidApiKey_returns401
  - X-API-Key: "wrong-key"
  - Assert 401

Test: validApiKey_proceedsToNextFilter
  - X-API-Key: "dev-key-12345"
  - Assert chain.doFilter WAS called
  - Assert SecurityContextHolder.getContext().getAuthentication() is not null

Test: healthEndpoint_noKey_passesThrough
  - GET /health with no key (filter should be excluded from this path via SecurityConfig)
  - This test is an integration test via @SpringBootTest — see Chunk 16.
```

---

## Chunk 16 — Integration Tests (Core Track)

**Objective:** Verify the full system behavior end-to-end using Testcontainers and HTTP via
`TestRestTemplate`. This chunk also defines the `BaseIntegrationTest` shared by all IT classes.

**Dependencies:** All Chunks 1–15 complete.

---

### 16.1 BaseIntegrationTest

Package: `src/test/java/.../BaseIntegrationTest.java`

```java
@Testcontainers
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
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
    @Autowired protected ReservationJpaRepository reservationRepository;
    @Autowired protected InventoryJpaRepository inventoryRepository;
    @Autowired protected ReservationEventJpaRepository eventRepository;
    @Autowired protected ExpiryStateJpaRepository expiryStateRepository;
    @Autowired protected ReservationExpiryJob expiryJob;

    protected static final String API_KEY = "dev-key-12345";
    protected static final HttpHeaders AUTH_HEADERS = new HttpHeaders();

    static {
        AUTH_HEADERS.set("X-API-Key", API_KEY);
    }

    @BeforeEach
    void cleanDatabase() {
        // Delete in FK-safe order
        eventRepository.deleteAll();
        reservationRepository.deleteAll();
        // Reset inventory to seed values via re-seeding or direct update
        inventoryRepository.findAll().forEach(inv -> {
            inv.setAvailableStock(inv.getTotalStock());
            inv.setReservedStock(0L);
            inventoryRepository.save(inv);
        });
    }
}
```

### 16.2 Core Integration Tests

**Test class:** `ReservationLifecycleIntegrationTest.java** (`@Tag("integration")`)

```
Test: createReservation_returns201WithPendingStatus
  POST /api/v1/reservations with {orderId: "ORD-IT-001", items: [{sku: "A100", quantity: 10}]}
  Assert: HTTP 201
  Assert: $.data.status == "PENDING"
  Assert: inventory.availableStock for A100 decreased by 10

Test: createAndConfirmReservation_fullLifecycle
  POST → confirm → GET
  Assert: final status == "CONFIRMED"
  Assert: reservation_events has CREATED + CONFIRMED rows

Test: createAndCancelReservation_stockRestored
  POST (reserve 30) → cancel → GET /inventory/A100
  Assert: availableStock restored to original value

Test: confirmAlreadyConfirmed_returns409WithInvalidStateTransition
  POST → confirm → confirm again
  Assert: second confirm returns HTTP 409, $.error.code == "INVALID_STATE_TRANSITION"

Test: cancelAlreadyCancelled_returns409
Test: confirmAfterCancel_returns409

Test: reserveWithInsufficientStock_returns409
  Seed A100 with available=10. POST requesting quantity=50.
  Assert: HTTP 409, $.error.code == "INSUFFICIENT_STOCK"

Test: multiSku_partialStockFailure_atomicRollback
  A100=50 available, B200=0 available.
  POST requesting A100:5 + B200:5 → assert HTTP 409.
  Assert: A100.availableStock unchanged (no partial allocation).

Test: duplicateOrderId_returns200WithExistingReservation
  POST twice with orderId="ORD-SAME"
  Assert: both return HTTP 200/201 with same reservationId.
  Assert: exactly one row in reservations table.

Test: getReservation_unknownId_returns404
  GET /api/v1/reservations/{random-uuid} → assert 404.

Test: listReservations_paginationRequired
  GET /api/v1/reservations (no params) → 400.
  GET /api/v1/reservations?page=0&size=20 → 200 with content array.

Test: listReservations_statusFilter_returnsOnlyMatchingStatus
  Create one PENDING, confirm one (→ CONFIRMED).
  GET ?page=0&size=20&status=PENDING → only PENDING returned.

Test: listReservations_invalidStatusFilter_returns400
  GET ?page=0&size=20&status=NONSENSE → 400, INVALID_REQUEST.

Test: security_missingApiKey_returns401
  POST /api/v1/reservations with no X-API-Key → 401, UNAUTHORIZED.

Test: security_healthEndpoint_publicAccess
  GET /health with no API key → 200.

Test: expiryJob_cancelsExpiredReservation_andReleasesStock
  Insert reservation with expiresAt = 1 hour ago (direct JPA bypass of validation).
  Call expiryJob.processExpiredReservations() directly.
  Assert: status = "CANCELLED".
  Assert: availableStock restored.
  Assert: reservation_events has CANCELLED row with event payload containing reason="TTL_EXPIRED".
```

### 16.3 Concurrent Integration Tests

**Test class:** `ConcurrentReservationIntegrationTest.java** (`@Tag("integration")`)

```java
@Test
@Tag("integration")
void concurrentReserve_sameSku_exactlyOneSucceeds() throws Exception {
    // Arrange: A100 has 100 available; both requests want 70.
    CountDownLatch ready = new CountDownLatch(2);
    CountDownLatch start = new CountDownLatch(1);
    AtomicInteger created = new AtomicInteger();
    AtomicInteger conflict = new AtomicInteger();

    Runnable task = () -> {
        ready.countDown();
        try {
            start.await();
            var response = restTemplate.exchange(
                "/api/v1/reservations",
                HttpMethod.POST,
                new HttpEntity<>(buildReserveRequest("ORD-" + UUID.randomUUID(), "A100", 70),
                    AUTH_HEADERS),
                ApiResponse.class);
            if (response.getStatusCode() == HttpStatus.CREATED) created.incrementAndGet();
            else if (response.getStatusCode() == HttpStatus.CONFLICT) conflict.incrementAndGet();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    };

    CompletableFuture.runAsync(task);
    CompletableFuture.runAsync(task);
    ready.await();
    start.countDown();
    Thread.sleep(1000);   // Allow both requests to complete.

    assertThat(created.get()).isEqualTo(1);
    assertThat(conflict.get()).isEqualTo(1);
    var inventory = inventoryRepository.findById("A100").orElseThrow();
    assertThat(inventory.getAvailableStock()).isEqualTo(30L);
}

@Test
@Tag("integration")
void concurrentConfirmAndCancel_exactlyOneSucceeds() throws Exception {
    // Create PENDING reservation first.
    // Then simultaneously confirm + cancel via two threads.
    // Assert exactly one 200, one 409, and final status is deterministic.
    ...
}

@Test
@Tag("integration")
void concurrentDuplicateOrderId_exactlyOneRowCreated() throws Exception {
    // Two simultaneous POST requests with same orderId.
    // Assert one reservation row in DB; both responses contain the same reservationId.
    ...
}
```

---

## Chunk 17 — NATS JetStream: Infrastructure Beans

**Objective:** Set up NATS `Connection` and `JetStream` beans, gated by
`@ConditionalOnProperty(name = "app.nats.enabled", havingValue = "true")`.

**Dependencies:** Chunk 1 (application.yml with `app.nats.*` properties), Chunk 8 (config structure).

---

### 17.1 NatsConfig

Package: `infrastructure/config/NatsConfig.java`

```java
@Configuration
@ConditionalOnProperty(name = "app.nats.enabled", havingValue = "true")
public class NatsConfig {

    /**
     * Creates the NATS connection with:
     *   - indefinite reconnect (maxReconnects = -1)
     *   - 2-second reconnect wait
     *   - connection event listener for observability
     */
    @Bean
    public Connection natsConnection(@Value("${app.nats.url}") String natsUrl)
            throws IOException, InterruptedException {
        Options options = new Options.Builder()
            .server(natsUrl)
            .connectionListener((conn, type) ->
                log.info("NATS connection event: {}", type))
            .reconnectWait(Duration.ofSeconds(2))
            .maxReconnects(-1)
            .build();
        return Nats.connect(options);
    }

    /** Provides the JetStream context from the established connection. */
    @Bean
    public JetStream jetStream(Connection connection) throws IOException {
        return connection.jetStream();
    }

    /** Provides the JetStream management context for stream lifecycle operations. */
    @Bean
    public JetStreamManagement jetStreamManagement(Connection connection) throws IOException {
        return connection.jetStreamManagement();
    }
}
```

### 17.2 Docker Compose addition (documented for Chunk 21)

The `nats` service definition with `-js` flag and `nats_data` volume is prepared here but applied in Chunk 21.

---

### 17.3 TDD — Chunk 17 Tests

**Test class:** `NatsConfigTest.java** (`@Tag("unit")`)

```
Test: natsConfig_disabled_beansNotCreated
  - Load application context with app.nats.enabled=false.
  - Assert: no NatsConfig bean, no Connection bean in context.

Test: natsConnection_buildsWithCorrectOptions
  - Uses a mock Nats.connect interceptor (constructor injection approach).
  - Verify: maxReconnects(-1) was set in options.
  - This is a unit test on the options builder — not a real network connection.
```

---

## Chunk 18 — NATS JetStream: NatsEventPublisher & StreamInitializer

**Objective:** Implement `NatsEventPublisher` (event delivery) and `NatsStreamInitializer`
(stream auto-create). Both are gated by `@ConditionalOnProperty`.

**Dependencies:** Chunk 5 (domain events), Chunk 9 (DomainEventSubscriber interface), Chunk 17 (NATS beans).

---

### 18.1 NatsEventPublisher

Package: `infrastructure/messaging/NatsEventPublisher.java`

```
Implements: DomainEventSubscriber, DisposableBean
@Component @ConditionalOnProperty(name = "app.nats.enabled", havingValue = "true")

Constructor: inject JetStream jetStream, Connection natsConnection, ObjectMapper objectMapper

on(DomainEvent event):
  1. Determine subject via toSubject(event.eventType()) — null for unknown types (log warn, return)
  2. Build NatsMessage record:
       messageId     = event.aggregateId() + ":" + event.eventType()
       eventType     = event.eventType()
       reservationId = event.aggregateId()
       orderId       = extracted from concrete event type via instanceof pattern matching
       occurredAt    = event.occurredAt()
       payload       = event-specific payload object (items/expiresAt for CREATED; reason for CANCELLED)
  3. Serialize NatsMessage to byte[] via objectMapper.writeValueAsBytes()
  4. Build PublishOptions with messageId for server-side deduplication
  5. jetStream.publish(subject, payload, opts) — get PubAck
  6. Log: "NATS publish OK: subject={}, seq={}, reservationId={}"
  7. CATCH IOException | JetStreamApiException:
       log.warn("NATS publish failed for reservationId={}...")
       DO NOT rethrow — NATS failure is non-fatal; outbox relay will recover

destroy(): close natsConnection if not already CLOSED

Private record NatsMessage(String messageId, String eventType, UUID reservationId,
                            String orderId, Instant occurredAt, Object payload)

Private toSubject(String eventType): String
  switch:
    "RESERVATION_CREATED"   → "reservations.created"
    "RESERVATION_CONFIRMED" → "reservations.confirmed"
    "RESERVATION_CANCELLED" → "reservations.cancelled"
    default → null (warn and skip)
```

### 18.2 NatsStreamInitializer

Package: `infrastructure/messaging/NatsStreamInitializer.java`

```
@Component @ConditionalOnProperty(name = "app.nats.enabled", havingValue = "true")
Implements: ApplicationRunner

Constructor: inject JetStreamManagement jsm

run(ApplicationArguments args):
  Build StreamConfiguration:
    name = "RESERVATIONS"
    subjects = ["reservations.created", "reservations.confirmed", "reservations.cancelled"]
    retentionPolicy = Limits
    storageType = File
    replicas = 1 (development; override to 3 for production via config)
    maxAge = 7 days
    maxBytes = 10 GB
    maximumMessageSize = 1 MB
    discardPolicy = DiscardNew
    duplicateWindow = 2 minutes
  try: jsm.addStream(config)
  catch JetStreamApiException where apiErrorCode == 10058:
    log.info("NATS stream RESERVATIONS already exists — skipping creation")
  catch all other JetStreamApiException: rethrow
```

---

### 18.3 TDD — Chunk 18 Tests

**Test class:** `NatsEventPublisherTest.java** (`@Tag("unit")`)

```
Mocks: JetStream (mock), Connection, ObjectMapper (real — use actual Jackson)

Test: on_reservationCreatedEvent_publishesToCorrectSubject
  - mock jetStream.publish returns a mock PubAck
  - call on(new ReservationCreatedEvent(...))
  - verify: jetStream.publish called with subject="reservations.created"
  - verify: PublishOptions messageId contains event UUID + ":RESERVATION_CREATED"

Test: on_reservationConfirmedEvent_publishesToConfirmedSubject
Test: on_reservationCancelledEvent_publishesToCancelledSubject

Test: on_natsPublishThrowsIOException_doesNotRethrow
  - mock jetStream.publish throws IOException
  - assertDoesNotThrow(() -> publisher.on(event))

Test: on_unknownEventType_skipsPublish
  - Create a fake DomainEvent with eventType = "UNKNOWN_TYPE"
  - verify: jetStream.publish is NEVER called
```

**Test class:** `NatsStreamInitializerTest.java** (`@Tag("unit")`)

```
Test: run_streamDoesNotExist_createsStream
  - mock jsm.addStream completes normally
  - verify: jsm.addStream called with name="RESERVATIONS"

Test: run_streamAlreadyExists_logsAndContinues
  - mock jsm.addStream throws JetStreamApiException with code 10058
  - assertDoesNotThrow(() -> initializer.run(args))
  - verify: no exception propagated

Test: run_unexpectedJetStreamException_propagates
  - mock throws JetStreamApiException with code != 10058
  - Assert exception propagates
```

---

## Chunk 19 — NATS JetStream: Outbox Relay

**Objective:** Implement `ReservationEventRelay`, the durability backstop that re-publishes events
with `published_at = NULL`.

**Dependencies:** Chunk 7 (ReservationEventJpaRepository), Chunk 18 (NatsEventPublisher), Chunk 5 (events).

---

### 19.1 ReservationEventRelay

Package: `infrastructure/messaging/ReservationEventRelay.java`

```java
/** Outbox relay — re-publishes events that were not delivered in the request path. */
@Component
@ConditionalOnProperty(name = "app.nats.enabled", havingValue = "true")
public class ReservationEventRelay {

    private final ReservationEventJpaRepository eventRepository;
    private final NatsEventPublisher natsPublisher;
    private final ObjectMapper objectMapper;

    /**
     * Polls for unpublished events every 30 seconds and re-delivers them to NATS.
     * Processes up to 50 events per run to avoid overwhelming the NATS cluster after an outage.
     */
    @Scheduled(fixedDelay = 30_000)
    @Transactional
    public void relayUnpublishedEvents() {
        var unpublished = eventRepository.findTop50ByPublishedAtIsNullOrderByCreatedAtAsc();
        if (unpublished.isEmpty()) {
            return;
        }
        log.info("Outbox relay: {} events pending", unpublished.size());

        for (var entity : unpublished) {
            try {
                var event = reconstruct(entity);
                natsPublisher.on(event);
                entity.setPublishedAt(Instant.now());
                eventRepository.save(entity);
            } catch (Exception e) {
                log.warn("Relay failed for event id={}: {}", entity.getId(), e.getMessage());
                // Continue to next event — one failure must not block the rest.
            }
        }
    }

    // Deserializes a ReservationEventEntity's JSON payload back to a DomainEvent.
    private DomainEvent reconstruct(ReservationEventEntity entity) throws JsonProcessingException {
        // Use entity.getEventType() to determine which record type to deserialize into.
        // "CREATED" → deserialize payload into ReservationCreatedEvent
        // "CONFIRMED" → ReservationConfirmedEvent
        // "CANCELLED" → ReservationCancelledEvent
        ...
    }
}
```

---

### 19.2 TDD — Chunk 19 Tests

**Test class:** `ReservationEventRelayTest.java** (`@Tag("unit")`)

```
Mocks: ReservationEventJpaRepository, NatsEventPublisher

Test: relayUnpublishedEvents_noEvents_doesNothing
  - stub findTop50... returns empty list
  - verify: natsPublisher.on never called

Test: relayUnpublishedEvents_oneEvent_publishesAndSetsPublishedAt
  - stub returns one entity with publishedAt=null
  - call relayUnpublishedEvents()
  - verify: natsPublisher.on called once
  - capture saved entity: assert publishedAt is not null

Test: relayUnpublishedEvents_publishFails_continuesAndLogs
  - stub returns two entities
  - natsPublisher.on throws RuntimeException on first call
  - verify: on() called for both entities (second entity still attempted)
  - verify: eventRepository.save called for second entity (first failed, second succeeded)
```

---

## Chunk 20 — NATS JetStream: Integration Tests

**Objective:** Verify NATS publish, outbox relay, server-side deduplication, and failure/recovery
scenarios against a real NATS JetStream server via Testcontainers.

**Dependencies:** Chunks 17–19 complete.

---

### 20.1 NatsBaseIntegrationTest

```java
@Testcontainers
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public abstract class NatsBaseIntegrationTest extends BaseIntegrationTest {

    @Container
    static final GenericContainer<?> NATS =
        new GenericContainer<>("nats:2.10-alpine")
            .withCommand("-js", "--store_dir=/data")
            .withExposedPorts(4222, 8222)
            .waitingFor(Wait.forHttp("/healthz").forPort(8222).withStartupTimeout(Duration.ofSeconds(30)));

    @DynamicPropertySource
    static void configureNatsProperties(DynamicPropertyRegistry registry) {
        registry.add("app.nats.enabled", () -> "true");
        registry.add("app.nats.url", () -> "nats://localhost:" + NATS.getMappedPort(4222));
    }
}
```

### 20.2 NATS Integration Tests

**Test class:** `NatsPublishIntegrationTest.java** (`@Tag("integration")`)

```
Test: postReservation_publishesEventToNatsAndSetsPublishedAt
  - POST /api/v1/reservations
  - Wait up to 2 seconds (Awaitility) for reservation_events row's published_at to be non-null.
  - Assert: published_at is not null.

Test: natsUnavailable_httpRequestSucceeds_eventInOutbox
  - Stop NATS container.
  - POST /api/v1/reservations.
  - Assert: HTTP 201 (NATS failure is invisible to client).
  - Assert: reservation_events row has published_at = null.
  - Restart NATS container. Wait for reconnect.
  - Trigger relay.relayUnpublishedEvents() directly.
  - Assert: published_at is now set.

Test: outboxRelay_seedsUnpublishedEvent_publishesAndMarksDelivered
  - Insert a reservation_events row with published_at = null directly.
  - Call relay.relayUnpublishedEvents().
  - Assert: published_at set.

Test: serverDeduplication_publishSameMessageIdTwice_natsReceivesOnce
  - Publish same event (same reservationId + eventType) via natsPublisher.on() twice.
  - Query NATS stream message count for subject via NATS HTTP monitoring API.
  - Assert: exactly 1 message in stream for that subject/messageId.
```

---

## Chunk 21 — Docker Compose & Operational Wiring

**Objective:** Final operational assembly — `docker-compose.yml`, `Dockerfile`, and validation that
`docker compose up` produces a working, testable system.

**Dependencies:** All previous chunks complete.

---

### 21.1 Dockerfile (multi-stage build)

```dockerfile
# Stage 1: Build
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /build
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN ./mvnw dependency:go-offline -q
COPY src ./src
RUN ./mvnw package -DskipTests -q

# Stage 2: Run
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=builder /build/target/reservation-service-*.jar app.jar

# Non-root user for security hardening
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

EXPOSE 8080
ENTRYPOINT ["java", "-XX:+UseZGC", "-Xms512m", "-Xmx1g", "-jar", "app.jar"]
```

### 21.2 docker-compose.yml (core track)

File: `docker-compose.yml`

```yaml
version: '3.9'

services:
  postgres:
    image: postgres:15-alpine
    environment:
      POSTGRES_DB: inventory_db
      POSTGRES_USER: app_user
      POSTGRES_PASSWORD: ${POSTGRES_PASSWORD:-secure_dev_password}
    ports:
      - "5432:5432"
    volumes:
      - postgres_data:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U app_user -d inventory_db"]
      interval: 5s
      timeout: 5s
      retries: 5
      start_period: 10s

  app:
    build:
      context: .
      dockerfile: Dockerfile
    environment:
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/inventory_db
      SPRING_DATASOURCE_USERNAME: app_user
      SPRING_DATASOURCE_PASSWORD: ${POSTGRES_PASSWORD:-secure_dev_password}
      API_KEYS: ${API_KEYS:-dev-key-12345}
      SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE: 50
      SPRING_DATASOURCE_HIKARI_MINIMUM_IDLE: 10
      SPRING_LIFECYCLE_TIMEOUT_PER_SHUTDOWN_PHASE: 30s
      NATS_ENABLED: "false"
    ports:
      - "8080:8080"
    depends_on:
      postgres:
        condition: service_healthy
    healthcheck:
      test: ["CMD", "wget", "--quiet", "--tries=1", "--spider", "http://localhost:8080/health"]
      interval: 10s
      timeout: 5s
      retries: 3
      start_period: 30s

volumes:
  postgres_data:

networks:
  default:
    name: inventory_network
```

### 21.3 docker-compose.nats.yml (Advanced Track A overlay)

File: `docker-compose.nats.yml`

```yaml
# Overlay for Advanced Track A: adds NATS JetStream
# Usage: docker compose -f docker-compose.yml -f docker-compose.nats.yml up
version: '3.9'

services:
  nats:
    image: nats:2.10-alpine
    command: ["-js", "--store_dir=/data"]
    ports:
      - "4222:4222"
      - "8222:8222"
    volumes:
      - nats_data:/data
    healthcheck:
      test: ["CMD", "wget", "--quiet", "--tries=1", "--spider", "http://localhost:8222/healthz"]
      interval: 5s
      timeout: 3s
      retries: 5

  app:
    environment:
      NATS_ENABLED: "true"
      NATS_URL: nats://nats:4222
    depends_on:
      nats:
        condition: service_healthy

volumes:
  nats_data:
```

### 21.4 README Quick-Start

Add to project root `README.md`:

```
## Quick Start

# Core track (PostgreSQL only)
docker compose up

# Advanced Track A (NATS JetStream)
docker compose -f docker-compose.yml -f docker-compose.nats.yml up

# Development API key: dev-key-12345
# Swagger UI: http://localhost:8080/swagger-ui.html
# Health: http://localhost:8080/health

## Run Tests
./mvnw test                           # unit + integration (requires Docker for Testcontainers)
./mvnw test -Dgroups=unit             # unit tests only (no Docker)
./mvnw test -Dgroups=integration      # integration tests only
./mvnw verify                         # tests + coverage report (target/site/jacoco/index.html)
```

---

## TDD Cycle Reference

Every feature in every chunk follows this cycle:

```
1. RED   — Write the test. Run it. Confirm it fails with a meaningful failure message,
           not a compilation error. If it fails with NPE or ClassNotFound, the test
           setup is wrong — fix that before writing implementation code.

2. GREEN — Write the MINIMUM code to make the test pass. No extra methods, no
           generalization, no "future-proofing". One failing test → one code change.

3. REFACTOR — With all tests green, clean up:
           - Extract private helpers for repeated logic.
           - Rename for clarity.
           - Verify line length ≤ 120.
           - Verify no comments that restate the code.
           - Run checkstyle: ./mvnw checkstyle:check

4. COMMIT — When the chunk is fully green and checkstyle passes.
           Commit message: "<Chunk N> — <one-line description>".
```

**Tagging tests:**
- All unit tests: `@Tag("unit")` at class level.
- All integration tests: `@Tag("integration")` at class level.
- This enables `./mvnw test -Dgroups=unit` (no Docker) for fast local feedback.

---

## Definition of Done Checklist

A chunk is done ONLY when all of the following are true:

**Code Quality:**
- [ ] `./mvnw checkstyle:check` passes (zero violations)
- [ ] No `import *` wildcard imports (except static test imports)
- [ ] All public classes have one-line class-level Javadoc
- [ ] All public interface methods have Javadoc with `@throws` where applicable
- [ ] No inline comments that restate the code
- [ ] No `// TODO` comments in committed code

**Layering:**
- [ ] `grep -r "org.springframework" src/main/java/.../domain/` returns zero results
- [ ] `grep -r "jakarta.persistence" src/main/java/.../domain/` returns zero results
- [ ] No service class imports a JPA entity directly (only via repository interface)

**Tests:**
- [ ] All unit tests pass: `./mvnw test -Dgroups=unit`
- [ ] All integration tests pass: `./mvnw test -Dgroups=integration`
- [ ] Test names follow `methodUnderTest_scenario_expectedBehavior` convention
- [ ] No `Thread.sleep()` except the documented concurrent integration test
- [ ] Coverage report shows ≥ 80% instruction coverage: `./mvnw verify`

**NATS Chunks (17–20 only):**
- [ ] All NATS beans annotated with `@ConditionalOnProperty(name = "app.nats.enabled", havingValue = "true")`
- [ ] Core track starts cleanly with `app.nats.enabled=false` (no NATS dependency at runtime)
- [ ] NATS failure does not propagate exceptions to the HTTP client

**Operational:**
- [ ] `docker compose up` starts cleanly and Swagger UI is accessible
- [ ] `GET /health` returns HTTP 200 with no API key
- [ ] `POST /api/v1/reservations` with `X-API-Key: dev-key-12345` creates a reservation

---

*End of Implementation Plan — Rev 1.0*
