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
