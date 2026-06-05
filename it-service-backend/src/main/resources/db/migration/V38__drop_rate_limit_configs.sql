-- Rate limiting is configured entirely through application.yml
-- (app.rate-limit.global-api.*, overridable via the RATE_LIMIT_GLOBAL_* env vars),
-- read by RateLimitConfigService as immutable @ConfigurationProperties.
--
-- The rate_limit_configs table (created in V19) was never read at runtime: the
-- service builds the config from configuration, not from the database, and the
-- repository / admin DTOs / admin UI panel were unused dead code. Dropping the
-- table removes the misleading "DB-driven" coupling so configuration lives in
-- exactly one place — the environment.
DROP TABLE IF EXISTS rate_limit_configs;
