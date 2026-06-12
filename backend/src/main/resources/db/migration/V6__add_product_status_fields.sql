-- V6: Agrega minimum_stock y active a productos (soft-delete + alertas de stock)

ALTER TABLE products
    ADD COLUMN IF NOT EXISTS minimum_stock INTEGER NOT NULL DEFAULT 0
        CHECK (minimum_stock >= 0),
    ADD COLUMN IF NOT EXISTS active BOOLEAN NOT NULL DEFAULT TRUE;

CREATE INDEX IF NOT EXISTS idx_products_active ON products(active);

-- Envers audita estas columnas; hay que agregarlas a la tabla _aud también
ALTER TABLE products_aud
    ADD COLUMN IF NOT EXISTS minimum_stock INTEGER,
    ADD COLUMN IF NOT EXISTS active BOOLEAN;
