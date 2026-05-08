-- V16: Add rate_limit_configs table and seed initial CLAIM_TICKET entry.
-- Managed by Flyway; JPA auto-DDL is disabled.

CREATE TABLE rate_limit_configs (
    id               BIGSERIAL     PRIMARY KEY,
    endpoint_key     VARCHAR(100)  NOT NULL UNIQUE,
    description      VARCHAR(255),
    max_requests     INT           NOT NULL DEFAULT 10,
    duration_seconds INT           NOT NULL DEFAULT 60,
    enabled          BOOLEAN       NOT NULL DEFAULT true,
    updated_at       TIMESTAMPTZ
);

-- Seed: claim endpoint starts at 10 requests per 60 seconds.
INSERT INTO rate_limit_configs (endpoint_key, description, max_requests, duration_seconds)
VALUES ('CLAIM_TICKET', 'Bilet claim limiti (saniyede)', 10, 60);
