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
