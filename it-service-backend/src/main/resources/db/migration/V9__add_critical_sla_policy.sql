-- V9: CRITICAL oncelik seviyesi icin SLA politikasi eklenir.
-- Mevcut V3 migrasyonu yalnizca LOW, MEDIUM, HIGH degerlerini iceriyordu.
-- tickets.priority → sla_policies.priority FK kisitlamasi nedeniyle
-- CRITICAL oncelikli bilet olusturabilmek icin bu kayit zorunludur.
INSERT INTO sla_policies (priority, target_resolution_hours) VALUES
('CRITICAL', 1)
ON CONFLICT (priority) DO NOTHING;
