-- liquibase formatted sql
-- changeset wirs:V001-create-products
CREATE TABLE products (
    sku         VARCHAR(50)  PRIMARY KEY,
    name        VARCHAR(255) NOT NULL,
    description TEXT,
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
-- rollback DROP TABLE products;
