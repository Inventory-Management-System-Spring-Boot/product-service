-- Flyway migration naming: V<version>__<description>.sql  (note the TWO underscores).
--
-- Flyway runs each script exactly once, in version order, and records it in a table called
-- flyway_schema_history along with a checksum of the file. That gives you two guarantees:
-- every environment ends up with the identical schema, and editing an already-applied
-- migration fails loudly instead of silently diverging.
--
-- The rule that follows from this: migrations are append-only. Once V1 has run anywhere,
-- you never edit it - you add V2.

CREATE TABLE products (
    id          BIGSERIAL     PRIMARY KEY,
    sku         VARCHAR(64)   NOT NULL,
    name        VARCHAR(255)  NOT NULL,
    description TEXT,
    price       NUMERIC(12, 2) NOT NULL,
    created_at  TIMESTAMPTZ   NOT NULL,
    updated_at  TIMESTAMPTZ   NOT NULL,

    -- Other services (inventory-service in Phase 3) will refer to products by SKU, not by
    -- our internal id. That makes uniqueness here a cross-service correctness requirement,
    -- not just a nicety. Enforcing it in the database is what makes it actually true:
    -- an application-level check alone loses to two concurrent requests.
    CONSTRAINT uq_products_sku UNIQUE (sku),

    -- Defence in depth. Bean Validation rejects a negative price at the API boundary; this
    -- also rejects it from a migration, a psql session, or a future bug in our own code.
    CONSTRAINT chk_products_price_non_negative CHECK (price >= 0)
);

-- No separate index on sku is needed: the UNIQUE constraint above creates one for us.

COMMENT ON TABLE  products      IS 'Product catalogue. Owned exclusively by product-service.';
COMMENT ON COLUMN products.sku IS 'Stable public business key used by other services.';
