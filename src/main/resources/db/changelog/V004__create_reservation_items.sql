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
