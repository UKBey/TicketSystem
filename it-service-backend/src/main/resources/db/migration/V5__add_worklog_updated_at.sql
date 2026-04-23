-- ticket_worklogs tablosuna güncelleme zaman damgası sütunu eklenir
ALTER TABLE ticket_worklogs ADD COLUMN updated_at TIMESTAMPTZ;
