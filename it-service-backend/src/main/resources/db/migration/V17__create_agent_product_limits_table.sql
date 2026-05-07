CREATE TABLE agent_product_limits (
    id BIGSERIAL PRIMARY KEY,
    agent_id VARCHAR(255) NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    product_id BIGINT NOT NULL REFERENCES products(id),
    use_custom_limit BOOLEAN NOT NULL DEFAULT FALSE,
    max_active_tickets INTEGER NULL,
    CONSTRAINT uq_agent_product_limit UNIQUE (agent_id, product_id)
);