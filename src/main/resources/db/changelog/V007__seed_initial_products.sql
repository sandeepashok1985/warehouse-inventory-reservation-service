-- liquibase formatted sql
-- changeset wirs:V007-seed-products
INSERT INTO products (sku, name, description) VALUES
    ('A100', 'Widget Alpha',    'Standard warehouse widget — category A'),
    ('B200', 'Bracket Beta',    'Mounting bracket for type-B assemblies'),
    ('C300', 'Connector Gamma', 'High-speed data connector, 3-port'),
    ('D400', 'Driver Delta',    'Precision driver set, 8-piece'),
    ('E500', 'Enclosure Echo',  'IP65 enclosure, 200x150x80mm');

INSERT INTO inventory (sku, total_stock, available_stock, reserved_stock) VALUES
    ('A100', 1000, 1000, 0),
    ('B200', 500,  500,  0),
    ('C300', 250,  250,  0),
    ('D400', 750,  750,  0),
    ('E500', 100,  100,  0);
-- rollback DELETE FROM inventory WHERE sku IN ('A100','B200','C300','D400','E500');
-- rollback DELETE FROM products WHERE sku IN ('A100','B200','C300','D400','E500');
