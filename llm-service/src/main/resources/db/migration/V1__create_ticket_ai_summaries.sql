-- Ticket AI özetlerini saklayan tablo.
-- Her ticket için birden fazla özet tutulabilir (geçmiş özetler korunur).
CREATE TABLE ticket_ai_summaries (
    id              BIGSERIAL       PRIMARY KEY,
    ticket_id       BIGINT          NOT NULL,
    model           VARCHAR(100)    NOT NULL,
    prompt_tokens   INTEGER,
    completion_tokens INTEGER,
    summary         TEXT            NOT NULL,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_ai_summaries_ticket_id ON ticket_ai_summaries(ticket_id);
CREATE INDEX idx_ai_summaries_created_at ON ticket_ai_summaries(created_at DESC);
