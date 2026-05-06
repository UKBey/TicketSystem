-- Per-event in-app notification preference columns.
-- Existing rows receive the DEFAULT TRUE so behaviour is unchanged for current users.
ALTER TABLE user_notification_preferences
    ADD COLUMN notify_on_ticket_created  BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN notify_on_ticket_assigned BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN notify_on_status_changed  BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN notify_on_comment_added   BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN notify_on_sla_warning     BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN notify_on_sla_breached    BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN notify_on_ticket_resolved BOOLEAN NOT NULL DEFAULT TRUE;
