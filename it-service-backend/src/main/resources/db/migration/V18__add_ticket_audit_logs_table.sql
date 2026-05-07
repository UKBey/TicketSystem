-- Audit log tablosu: ticket aksiyonlari ve notlar
CREATE TABLE ticket_audit_logs (
    id BIGSERIAL PRIMARY KEY,
    ticket_id BIGINT NOT NULL REFERENCES tickets(id) ON DELETE CASCADE,
    actor_id VARCHAR(36) NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    action_type VARCHAR(30) NOT NULL,
    note TEXT,
    previous_state VARCHAR(30),
    new_state VARCHAR(30),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_ticket_audit_logs_ticket_id ON ticket_audit_logs(ticket_id);
CREATE INDEX idx_ticket_audit_logs_created_at ON ticket_audit_logs(created_at);
