INSERT INTO rate_limit_configs (endpoint_key, description, max_requests, duration_seconds, enabled, updated_at)
VALUES 
    ('GLOBAL_API', 'Genel API İstek Limiti (Spam Koruması)', 300, 60, true, CURRENT_TIMESTAMP);
