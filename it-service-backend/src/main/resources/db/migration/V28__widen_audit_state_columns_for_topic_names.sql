-- TOPIC_CHANGE audit kayıtlarında previous/new state alanlarına topic adı yazıyoruz.
-- Topic adı VARCHAR(255), eski VARCHAR(30) durum kodlarına da uyumlu kalıyor.

ALTER TABLE ticket_audit_logs
    ALTER COLUMN previous_state TYPE VARCHAR(255);

ALTER TABLE ticket_audit_logs
    ALTER COLUMN new_state TYPE VARCHAR(255);
