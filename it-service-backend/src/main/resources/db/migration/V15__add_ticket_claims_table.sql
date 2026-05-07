-- Birden fazla ajanin ayni bileti sahiplenebilmesi icin claims tablosu
CREATE TABLE ticket_claims (
    id           BIGSERIAL    PRIMARY KEY,
    ticket_id    BIGINT       NOT NULL REFERENCES tickets(id) ON DELETE CASCADE,
    agent_id     VARCHAR(255) NOT NULL REFERENCES users(id)  ON DELETE CASCADE,
    claimed_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_ticket_agent_claim UNIQUE (ticket_id, agent_id)
);

CREATE INDEX idx_ticket_claims_ticket_id ON ticket_claims(ticket_id);
CREATE INDEX idx_ticket_claims_agent_id  ON ticket_claims(agent_id);

-- Mevcut tekli atama verilerini yeni cok-agentli yapiya tasir
INSERT INTO ticket_claims (ticket_id, agent_id, claimed_at)
SELECT id, assignee_id, COALESCE(created_at, CURRENT_TIMESTAMP)
FROM   tickets
WHERE  assignee_id IS NOT NULL;

-- Eskiyen tek-agent sutununu kaldirir
ALTER TABLE tickets DROP COLUMN assignee_id;
