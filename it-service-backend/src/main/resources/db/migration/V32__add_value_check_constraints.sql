-- V32: Data integrity için CHECK constraint'ler.
-- Frontend ve service-level validation iyi durumda olsa da DB-level savunma
-- defense-in-depth: doğrudan SQL ile veya bug'lı yeni client tarafından
-- invalid veri yazılmasını önler.
--
-- - ticket_worklogs.minutes: > 0 (negatif veya sıfır dakika anlamsız;
--   metric hesaplamalarını bozar)
-- - csat_surveys.rating: 1..5 aralığında (CSAT 5'li Likert ölçeği)

ALTER TABLE ticket_worklogs
    ADD CONSTRAINT chk_worklog_minutes_positive
    CHECK (minutes > 0);

ALTER TABLE csat_surveys
    ADD CONSTRAINT chk_csat_rating_range
    CHECK (rating BETWEEN 1 AND 5);
