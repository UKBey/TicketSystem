-- Agent/lead kullanıcıların sol menüdeki ticket panellerini (workspace, pool, history,
-- team, all-tickets) açıp kapatma tercihleri. Frontend'in tanımladığı opak JSON string
-- olarak saklanır; null ise tüm paneller görünür (varsayılan).
ALTER TABLE users
    ADD COLUMN IF NOT EXISTS panel_preferences VARCHAR(500);
