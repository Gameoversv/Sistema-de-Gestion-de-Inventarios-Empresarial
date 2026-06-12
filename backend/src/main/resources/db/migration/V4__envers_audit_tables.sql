-- V4: Hibernate Envers audit tables
-- Matches RevisionInfo entity + @Audited on Category, Product, StockMovement, AppUser

CREATE TABLE IF NOT EXISTS revinfo (
    rev      SERIAL  PRIMARY KEY,
    revtstmp BIGINT  NOT NULL
);

CREATE TABLE IF NOT EXISTS categories_aud (
    id          BIGINT       NOT NULL,
    rev         INTEGER      NOT NULL,
    revtype     SMALLINT,
    name        VARCHAR(100),
    description TEXT,
    created_at  TIMESTAMP,
    updated_at  TIMESTAMP,
    PRIMARY KEY (id, rev),
    CONSTRAINT fk_categories_aud_rev FOREIGN KEY (rev) REFERENCES revinfo(rev)
);

CREATE TABLE IF NOT EXISTS products_aud (
    id          BIGINT         NOT NULL,
    rev         INTEGER        NOT NULL,
    revtype     SMALLINT,
    sku         VARCHAR(100),
    name        VARCHAR(255),
    description TEXT,
    price       NUMERIC(15, 2),
    stock       INTEGER,
    category_id BIGINT,
    created_at  TIMESTAMP,
    updated_at  TIMESTAMP,
    PRIMARY KEY (id, rev),
    CONSTRAINT fk_products_aud_rev FOREIGN KEY (rev) REFERENCES revinfo(rev)
);

CREATE TABLE IF NOT EXISTS stock_movements_aud (
    id           BIGINT       NOT NULL,
    rev          INTEGER      NOT NULL,
    revtype      SMALLINT,
    product_id   BIGINT,
    type         VARCHAR(20),
    quantity     INTEGER,
    reason       VARCHAR(500),
    reference_id VARCHAR(255),
    created_at   TIMESTAMP,
    updated_at   TIMESTAMP,
    PRIMARY KEY (id, rev),
    CONSTRAINT fk_stock_movements_aud_rev FOREIGN KEY (rev) REFERENCES revinfo(rev)
);

CREATE TABLE IF NOT EXISTS app_users_aud (
    id           BIGINT       NOT NULL,
    rev          INTEGER      NOT NULL,
    revtype      SMALLINT,
    keycloak_id  UUID,
    email        VARCHAR(255),
    display_name VARCHAR(100),
    role         VARCHAR(20),
    enabled      BOOLEAN,
    created_at   TIMESTAMP,
    updated_at   TIMESTAMP,
    PRIMARY KEY (id, rev),
    CONSTRAINT fk_app_users_aud_rev FOREIGN KEY (rev) REFERENCES revinfo(rev)
);
