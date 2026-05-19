-- V30: SLA warning idempotency için flag kolonu.
-- Scheduler (SlaNotificationScheduler.checkUpcomingSlaBreaches) deadline'ı
-- yaklaşan biletler için her 15 dk'da mail atıyordu — flag yoktu, aynı bilete
-- 96 mail/gün gidebilirdi. Bu kolon "uyarı maili gönderildi" durumunu kalıcı
-- tutar; query bu kolonu NULL olan biletleri seçer, mail gönderildikten sonra
-- timestamp set edilir.

ALTER TABLE tickets
    ADD COLUMN sla_warning_sent_at TIMESTAMP WITH TIME ZONE;

-- Scheduler taraması her cycle'de bu kolonu kontrol edecek; index açık olanları
-- (henüz gönderilmemiş) hızlı filtrelemek için NULL-only partial index.
CREATE INDEX idx_tickets_sla_warning_pending
    ON tickets (sla_deadline)
    WHERE sla_warning_sent_at IS NULL AND sla_breached = false;
