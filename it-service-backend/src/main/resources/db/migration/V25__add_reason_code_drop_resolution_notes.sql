-- Aksiyon nedeni (RESOLVE, CLOSE, UNCLAIM vb.) artık audit log üzerinden takip ediliyor.
-- reason_code: önceden tanımlı bir sebep kodunu tutar (örn. SOLUTION_PROVIDED, WORKLOAD).
-- note kolonu serbest metin için saklanmaya devam eder.
ALTER TABLE ticket_audit_logs
    ADD COLUMN IF NOT EXISTS reason_code VARCHAR(50);

-- ticket_resolution_notes tablosu artık gereksiz: çözüm bilgisi audit log'un en güncel
-- RESOLVE kaydından okunabiliyor. Bu tablo ve tüm verisi kalıcı olarak kaldırılıyor.
DROP TABLE IF EXISTS ticket_resolution_notes;
