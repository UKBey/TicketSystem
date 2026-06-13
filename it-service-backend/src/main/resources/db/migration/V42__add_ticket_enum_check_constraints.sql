-- V42: status / priority / comment-type değerleri için CHECK constraint'ler.
-- Bu kolonlar Java tarafında @Enumerated(EnumType.STRING) enum'larıyla
-- (TicketStatus, Priority, CommentType) eşleşir; değerler enum name()'leridir.
-- V32/V35 ile aynı defense-in-depth deseni: frontend ve service-level validation
-- iyi durumda olsa da, doğrudan SQL veya bug'lı bir client'ın geçersiz değer
-- yazmasını DB seviyesinde engeller. Kolonlar VARCHAR kalır (esnek migration),
-- CHECK yalnızca izin verilen küme'yi sabitler.

ALTER TABLE tickets
    ADD CONSTRAINT chk_ticket_status
    CHECK (status IN ('NEW', 'IN_PROGRESS', 'WAITING_FOR_CUSTOMER', 'RESOLVED', 'CLOSED'));

ALTER TABLE tickets
    ADD CONSTRAINT chk_ticket_priority
    CHECK (priority IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL'));

ALTER TABLE ticket_comments
    ADD CONSTRAINT chk_comment_type
    CHECK (type IN ('EXTERNAL', 'INTERNAL'));
