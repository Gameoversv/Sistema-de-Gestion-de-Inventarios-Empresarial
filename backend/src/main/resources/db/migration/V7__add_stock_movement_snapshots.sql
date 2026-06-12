-- V7: Stock movement snapshots + Envers username tracking

-- Snapshot columns on stock_movements
ALTER TABLE stock_movements
    ADD COLUMN IF NOT EXISTS quantity_before INTEGER,
    ADD COLUMN IF NOT EXISTS quantity_after  INTEGER,
    ADD COLUMN IF NOT EXISTS performed_by    VARCHAR(100);

-- Allow quantity=0 (ADJUSTMENT can set stock to zero)
ALTER TABLE stock_movements DROP CONSTRAINT IF EXISTS stock_movements_quantity_check;
ALTER TABLE stock_movements ADD CONSTRAINT stock_movements_quantity_check CHECK (quantity >= 0);

-- Mirror snapshot columns in Envers audit table
ALTER TABLE stock_movements_aud
    ADD COLUMN IF NOT EXISTS quantity_before INTEGER,
    ADD COLUMN IF NOT EXISTS quantity_after  INTEGER,
    ADD COLUMN IF NOT EXISTS performed_by    VARCHAR(100);

-- Username tracking in Envers revision table
ALTER TABLE revinfo
    ADD COLUMN IF NOT EXISTS username VARCHAR(100);
