-- RBAC yeniden tasarimi: tek-kolon users.role -> additive cok-rol modeli.
-- user_roles, bir kullanicinin sahip oldugu TUM rolleri tutar (Keycloak'tan senkronlanir).
-- users.role kolonu "birincil/gosterim rolu" olarak korunur (geriye uyumluluk); yetkilendirme
-- JWT authority'lerinden gelir, bu tablo user-management UI'i ve sunucu-tarafi rol sorgulari icindir.
CREATE TABLE user_roles (
    user_id VARCHAR(255) NOT NULL,
    role    VARCHAR(20)  NOT NULL,
    PRIMARY KEY (user_id, role),
    CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE INDEX idx_user_roles_role ON user_roles (role);

-- Backfill: mevcut tek-rolu cok-rol kumesine tasi.
-- customer/agent/manager dogrudan; AGENT_ADMIN ise LEAD_AGENT + ADMIN olarak ikiye ayrilir.
INSERT INTO user_roles (user_id, role)
SELECT id, role FROM users
WHERE role IS NOT NULL AND role <> 'AGENT_ADMIN'
ON CONFLICT DO NOTHING;

INSERT INTO user_roles (user_id, role)
SELECT id, 'LEAD_AGENT' FROM users WHERE role = 'AGENT_ADMIN'
ON CONFLICT DO NOTHING;

INSERT INTO user_roles (user_id, role)
SELECT id, 'ADMIN' FROM users WHERE role = 'AGENT_ADMIN'
ON CONFLICT DO NOTHING;
