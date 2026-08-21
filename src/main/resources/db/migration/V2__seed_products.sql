-- Development seed data.
--
-- These SKUs are the ones inventory-service will hold stock for in Phase 3, so having them
-- present from the start saves you re-creating test data every time you reset the database.
--
-- Worth knowing: putting seed data in a Flyway migration is a shortcut, not a best practice.
-- It runs in production too. Real projects keep reference data (currencies, country codes)
-- in migrations, and test data in a dev-only mechanism - a Spring profile-specific
-- CommandLineRunner, or Flyway's own `spring.flyway.locations` pointing at an extra folder
-- only under the local profile. We accept the shortcut here and move on.

INSERT INTO products (sku, name, description, price, created_at, updated_at) VALUES
    ('IPHONE-15',    'iPhone 15',              'Apple smartphone, 128GB',      79999.00, NOW(), NOW()),
    ('PIXEL-9',      'Google Pixel 9',         'Android smartphone, 256GB',    69999.00, NOW(), NOW()),
    ('MBP-14-M4',    'MacBook Pro 14 M4',      'Apple laptop, 16GB/512GB',    199999.00, NOW(), NOW()),
    ('AIRPODS-PRO2', 'AirPods Pro 2',          'Noise-cancelling earbuds',      24999.00, NOW(), NOW()),
    ('DELL-U2723',   'Dell UltraSharp U2723QE','27-inch 4K USB-C monitor',      54999.00, NOW(), NOW());
