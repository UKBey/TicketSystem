-- V29: Şifre sıfırlama token tablosu.
-- Kullanıcı "şifremi unuttum" akışı için mail ile gönderilen token'lar burada saklanır.
-- Token kullanılana (used_at) veya süresi dolana (expires_at) kadar geçerlidir.
-- Eski şifre, token tüketilene kadar çalışmaya devam eder; bu tablo şifrenin
-- Keycloak/LDAP tarafındaki halini etkilemez.

CREATE TABLE password_reset_tokens (
    id           BIGSERIAL PRIMARY KEY,
    user_id      VARCHAR(255) NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash   VARCHAR(255) NOT NULL UNIQUE,
    created_at   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    expires_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    used_at      TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_password_reset_tokens_user_id ON password_reset_tokens(user_id);
CREATE INDEX idx_password_reset_tokens_expires ON password_reset_tokens(expires_at) WHERE used_at IS NULL;
