-- V20: SLA politikalarına uyarı eşiği (saat) eklenir.
-- warning_threshold_hours: SLA deadline'ına bu kadar saat kala uyarı gönderilir.
ALTER TABLE sla_policies
    ADD COLUMN IF NOT EXISTS warning_threshold_hours INTEGER NOT NULL DEFAULT 2;
