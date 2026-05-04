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

-- Default partition catches rows outside explicit partitions.
CREATE TABLE reservation_events_default PARTITION OF reservation_events DEFAULT;

CREATE INDEX idx_reservation_events_reservation_id ON reservation_events(reservation_id);
CREATE INDEX idx_reservation_events_unpublished
    ON reservation_events(created_at) WHERE published_at IS NULL;
-- rollback DROP TABLE reservation_events;
