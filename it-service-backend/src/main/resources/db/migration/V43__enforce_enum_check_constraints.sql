-- Defense-in-depth CHECK constraints for the columns now modeled as Java enums.
-- The application persists each via @Enumerated(STRING) (or a code AttributeConverter
-- for language/theme), so these constraints simply mirror the enum value sets at the DB
-- level — matching the pattern V42 established for ticket status/priority/comment type.
-- Append-only audit columns (ticket_audit_logs.action_type / reason_code) are intentionally
-- left unconstrained: they must tolerate retired values on read.

-- sla_policies.priority → Priority
ALTER TABLE sla_policies
    ADD CONSTRAINT chk_sla_policy_priority
    CHECK (priority IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL'));

-- notifications.type → NotificationType
ALTER TABLE notifications
    ADD CONSTRAINT chk_notification_type
    CHECK (type IN ('TICKET_CREATED', 'TICKET_ASSIGNED', 'TICKET_STATUS_CHANGED',
                    'COMMENT_ADDED', 'SLA_WARNING', 'SLA_BREACHED', 'TICKET_RESOLVED', 'GENERAL'));

-- notifications.reference_type → NotificationReferenceType (nullable; CHECK passes on NULL).
-- Normalize any legacy lower-case tags first so the constraint validates cleanly.
UPDATE notifications SET reference_type = upper(reference_type) WHERE reference_type IS NOT NULL;
ALTER TABLE notifications
    ADD CONSTRAINT chk_notification_reference_type
    CHECK (reference_type IS NULL OR reference_type IN ('TICKET', 'COMMENT', 'CSAT'));

-- users.preferred_language → Language (stored as lower-case ISO code)
ALTER TABLE users
    ADD CONSTRAINT chk_user_preferred_language
    CHECK (preferred_language IN ('en', 'tr'));

-- users.preferred_theme → Theme (stored as lower-case token)
ALTER TABLE users
    ADD CONSTRAINT chk_user_preferred_theme
    CHECK (preferred_theme IN ('light', 'dark'));

-- users.preferred_date_format → DateFormat
ALTER TABLE users
    ADD CONSTRAINT chk_user_preferred_date_format
    CHECK (preferred_date_format IN ('DMY_SLASH', 'MDY_SLASH', 'YMD_DASH', 'DMY_DOT', 'MED'));
