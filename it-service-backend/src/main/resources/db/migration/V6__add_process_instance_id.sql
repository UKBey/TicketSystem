-- V6: Ticket tablosuna jBPM süreç örneği bağlantısı eklenir
-- Bu kolon, her ticket'ın KIE Server'daki hangi workflow sürecine bağlı olduğunu tutar.
-- Mevcut ticket'lar için NULL kalacaktır (geriye dönük uyumluluk).
ALTER TABLE tickets ADD COLUMN process_instance_id BIGINT;

-- İleride sorgu performansı için index
CREATE INDEX idx_tickets_process_instance_id ON tickets (process_instance_id);
