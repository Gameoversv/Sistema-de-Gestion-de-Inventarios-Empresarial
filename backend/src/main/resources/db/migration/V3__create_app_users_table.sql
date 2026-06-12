-- V3: AppUser table — local Keycloak mirror (replaces local-auth users table)

CREATE TABLE IF NOT EXISTS app_users (
    id           BIGSERIAL    PRIMARY KEY,
    keycloak_id  UUID         NOT NULL UNIQUE,
    email        VARCHAR(255) NOT NULL UNIQUE,
    display_name VARCHAR(100),
    role         VARCHAR(20)  NOT NULL DEFAULT 'VIEWER',
    enabled      BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at   TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_app_users_keycloak_id ON app_users(keycloak_id);
CREATE INDEX idx_app_users_email       ON app_users(email);
