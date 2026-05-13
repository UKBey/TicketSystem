DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'chk_ticket_comments_message_length'
    ) THEN
        ALTER TABLE ticket_comments
            ADD CONSTRAINT chk_ticket_comments_message_length
            CHECK (char_length(message) <= 500);
    END IF;
END $$;
