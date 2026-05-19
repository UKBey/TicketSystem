-- V31: Ticket child tabloların FK'lerine ON DELETE CASCADE ekler.
-- Şu an "bilet sil" feature'ı uygulamada yok (sadece CLOSE), ama ileride
-- hard delete eklendiğinde veya operatör manuel DELETE çalıştırırsa yorum,
-- worklog, attachment ve csat kayıtları yetim kalmasın diye proactive fix.
-- V1'de FK'ler default RESTRICT ile yaratılmıştı; mevcut constraint'i drop
-- edip CASCADE ile yeniden ekliyoruz.

-- ticket_comments → tickets
ALTER TABLE ticket_comments
    DROP CONSTRAINT IF EXISTS fk_comment_ticket;
ALTER TABLE ticket_comments
    ADD CONSTRAINT fk_comment_ticket
    FOREIGN KEY (ticket_id) REFERENCES tickets(id) ON DELETE CASCADE;

-- ticket_worklogs → tickets
ALTER TABLE ticket_worklogs
    DROP CONSTRAINT IF EXISTS fk_worklog_ticket;
ALTER TABLE ticket_worklogs
    ADD CONSTRAINT fk_worklog_ticket
    FOREIGN KEY (ticket_id) REFERENCES tickets(id) ON DELETE CASCADE;

-- ticket_attachments → tickets
ALTER TABLE ticket_attachments
    DROP CONSTRAINT IF EXISTS fk_attachment_ticket;
ALTER TABLE ticket_attachments
    ADD CONSTRAINT fk_attachment_ticket
    FOREIGN KEY (ticket_id) REFERENCES tickets(id) ON DELETE CASCADE;

-- csat_surveys → tickets
ALTER TABLE csat_surveys
    DROP CONSTRAINT IF EXISTS fk_csat_ticket;
ALTER TABLE csat_surveys
    ADD CONSTRAINT fk_csat_ticket
    FOREIGN KEY (ticket_id) REFERENCES tickets(id) ON DELETE CASCADE;
