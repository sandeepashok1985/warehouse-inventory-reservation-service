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
