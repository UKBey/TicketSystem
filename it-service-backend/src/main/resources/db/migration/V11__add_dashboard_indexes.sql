-- V11: Dashboard sorgu performansı için indeksler
-- Metrics endpoint'lerinde sık kullanılan sütunlara index eklenir.

CREATE INDEX IF NOT EXISTS idx_tickets_status
    ON tickets (status);

CREATE INDEX IF NOT EXISTS idx_tickets_priority
    ON tickets (priority);

CREATE INDEX IF NOT EXISTS idx_tickets_assignee_id
    ON tickets (assignee_id);

CREATE INDEX IF NOT EXISTS idx_tickets_created_at
    ON tickets (created_at);

CREATE INDEX IF NOT EXISTS idx_tickets_resolved_at
    ON tickets (resolved_at);

CREATE INDEX IF NOT EXISTS idx_tickets_sla_breached
    ON tickets (sla_breached);

CREATE INDEX IF NOT EXISTS idx_tickets_sla_deadline
    ON tickets (sla_deadline);

CREATE INDEX IF NOT EXISTS idx_ticket_worklogs_agent_id
    ON ticket_worklogs (agent_id);

CREATE INDEX IF NOT EXISTS idx_ticket_worklogs_created_at
    ON ticket_worklogs (created_at);

CREATE INDEX IF NOT EXISTS idx_csat_created_at
    ON csat (created_at);

CREATE INDEX IF NOT EXISTS idx_csat_rating
    ON csat (rating);
