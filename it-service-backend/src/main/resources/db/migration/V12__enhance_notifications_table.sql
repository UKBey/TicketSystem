-- Bildirim tipini, ilgili kaydı ve e-posta gönderim durumunu tutan kolonlar eklendi.
ALTER TABLE notifications
    ADD COLUMN type           VARCHAR(50)  NOT NULL DEFAULT 'GENERAL',
    ADD COLUMN reference_id   BIGINT,
    ADD COLUMN reference_type VARCHAR(50),
    ADD COLUMN email_sent     BOOLEAN      DEFAULT FALSE;
