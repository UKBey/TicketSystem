-- Notification re-localization (i18n).
--
-- Previously a notification's text was rendered into a single language and frozen
-- in the `message` TEXT column at write time; switching the user's language did
-- nothing. From V33 onwards notifications instead store a MessageSource KEY plus
-- structured ARGS, and the text is rendered at READ time in the requesting user's
-- current preferred language.
--
-- This migration is additive and safe on existing data:
--   * `message_key`  — the MessageSource key (e.g. notification.ticket.created).
--   * `message_args` — the ordered substitution arguments, stored as a JSON array.
--   * `message`      — kept, but NOT NULL is dropped so new (key-bearing) rows can
--                      leave it NULL. Pre-V33 rows keep their original rendered
--                      text in `message` (message_key/message_args stay NULL) and
--                      continue to render via the legacy fallback path until they
--                      auto-purge.

ALTER TABLE notifications ADD COLUMN message_key VARCHAR(160);
ALTER TABLE notifications ADD COLUMN message_args JSONB;
ALTER TABLE notifications ALTER COLUMN message DROP NOT NULL;
