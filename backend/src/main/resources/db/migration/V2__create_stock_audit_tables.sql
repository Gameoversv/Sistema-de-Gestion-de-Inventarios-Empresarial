-- V2: Stock movements and audit log tables

CREATE TABLE IF NOT EXISTS stock_movements (
    id           BIGSERIAL PRIMARY KEY,
    product_id   BIGINT        NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    type         VARCHAR(20)   NOT NULL CHECK (type IN ('IN', 'OUT', 'ADJUSTMENT')),
    quantity     INTEGER       NOT NULL CHECK (quantity > 0),
    reason       VARCHAR(500),
    reference_id VARCHAR(255),
    created_at   TIMESTAMP     NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMP     NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_stock_movements_product ON stock_movements(product_id);
CREATE INDEX idx_stock_movements_type    ON stock_movements(type);
CREATE INDEX idx_stock_movements_created ON stock_movements(created_at);

CREATE TABLE IF NOT EXISTS audit_logs (
    id           BIGSERIAL PRIMARY KEY,
    entity_type  VARCHAR(50)  NOT NULL,
    entity_id    BIGINT       NOT NULL,
    action       VARCHAR(20)  NOT NULL CHECK (action IN ('CREATE', 'UPDATE', 'DELETE')),
    performed_by VARCHAR(100),
    detail       TEXT,
    created_at   TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_audit_logs_entity  ON audit_logs(entity_type, entity_id);
CREATE INDEX idx_audit_logs_user    ON audit_logs(performed_by);
CREATE INDEX idx_audit_logs_created ON audit_logs(created_at);
