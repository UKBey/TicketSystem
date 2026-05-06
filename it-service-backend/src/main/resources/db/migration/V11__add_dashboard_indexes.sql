-- Dashboard metrikleri için performans indexleri
-- MetricsService sorgularını hızlandırır

-- Status bazlı filtreleme (countGroupedByStatus, findByStatus)
CREATE INDEX IF NOT EXISTS idx_tickets_status ON tickets(status);

-- Priority bazlı gruplama (getDashboardSummary priority distribution)
CREATE INDEX IF NOT EXISTS idx_tickets_priority ON tickets(priority);

-- Agent performans sorguları (assignee_id + status birlikte sıkça kullanılır)
CREATE INDEX IF NOT EXISTS idx_tickets_assignee_id ON tickets(assignee_id);
CREATE INDEX IF NOT EXISTS idx_tickets_assignee_status ON tickets(assignee_id, status);

-- Zaman bazlı sorgular (timeline, avgResolutionTime)
CREATE INDEX IF NOT EXISTS idx_tickets_created_at ON tickets(created_at);
CREATE INDEX IF NOT EXISTS idx_tickets_resolved_at ON tickets(resolved_at);
CREATE INDEX IF NOT EXISTS idx_tickets_closed_at ON tickets(closed_at);

-- SLA metrikleri
CREATE INDEX IF NOT EXISTS idx_tickets_sla_breached ON tickets(sla_breached) WHERE sla_breached = true;
CREATE INDEX IF NOT EXISTS idx_tickets_sla_deadline ON tickets(sla_deadline);

-- Worklog sorguları (getAgentPerformance worklog dakika toplamı)
CREATE INDEX IF NOT EXISTS idx_ticket_worklogs_agent_id ON ticket_worklogs(agent_id);
CREATE INDEX IF NOT EXISTS idx_ticket_worklogs_created_at ON ticket_worklogs(created_at);

-- CSAT sorguları (average rating, count)
CREATE INDEX IF NOT EXISTS idx_csat_created_at ON csat_surveys(created_at);
CREATE INDEX IF NOT EXISTS idx_csat_rating ON csat_surveys(rating);

-- ProductMetrics: product_id + status birleşimi
CREATE INDEX IF NOT EXISTS idx_tickets_product_id ON tickets(product_id);
CREATE INDEX IF NOT EXISTS idx_tickets_product_status ON tickets(product_id, status);
