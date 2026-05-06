-- Kullanıcı başına e-posta bildirim tercihlerini saklayan tablo.
-- Satır yoksa uygulama katmanında tüm tercihler true (açık) kabul edilir.
CREATE TABLE user_notification_preferences (
    user_id                  VARCHAR(36)  PRIMARY KEY,
    email_on_ticket_created  BOOLEAN      NOT NULL DEFAULT TRUE,
    email_on_ticket_assigned BOOLEAN      NOT NULL DEFAULT TRUE,
    email_on_status_changed  BOOLEAN      NOT NULL DEFAULT TRUE,
    email_on_comment_added   BOOLEAN      NOT NULL DEFAULT TRUE,
    email_on_sla_warning     BOOLEAN      NOT NULL DEFAULT TRUE,
    email_on_sla_breached    BOOLEAN      NOT NULL DEFAULT TRUE,
    email_on_ticket_resolved BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at               TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at               TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_notif_pref_user FOREIGN KEY (user_id) REFERENCES users(id)
);
