-- Rol/ürün erişim talepleri tablosu.
-- Rolsüz kullanıcılar serbest metin ile talepte bulunur;
-- admin talebi okur, siler ve üzerinden rol/ürün ataması yapabilir.
CREATE TABLE access_requests (
    id          BIGSERIAL PRIMARY KEY,
    user_id     VARCHAR(36) NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    message     TEXT        NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_access_requests_user_id ON access_requests(user_id);
CREATE INDEX idx_access_requests_created_at ON access_requests(created_at DESC);
