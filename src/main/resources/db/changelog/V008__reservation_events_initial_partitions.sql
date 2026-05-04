-- liquibase formatted sql
-- changeset wirs:V008-reservation-events-initial-partitions splitStatements:false
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
