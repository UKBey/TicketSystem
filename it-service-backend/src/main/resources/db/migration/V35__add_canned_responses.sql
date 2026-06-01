-- Hazır yanıtlar (canned responses) — ajanların yorum yazarken tek tıkla ekleyebildiği
-- yeniden kullanılabilir metin şablonları. İki katman:
--   * PERSONAL  : sahibi agent'a özel (owner_agent_id), ürüne bağlanmaz.
--   * SHARED    : ekip geneli; admin/manager yönetir, opsiyonel olarak bir ürüne (product_id) bağlanabilir.
-- Görünürlük (visibility) yorum tipiyle hizalanır: EXTERNAL (müşteriye), INTERNAL (dahili not) veya BOTH.
-- İçerik iki dilde tutulur (content_tr / content_en); en az biri dolu olmalı. Placeholder'lar
-- ({{musteri.ad}} vb.) ham olarak saklanır ve istemci tarafında doldurulur.
CREATE TABLE IF NOT EXISTS canned_responses (
    id              BIGSERIAL    PRIMARY KEY,
    title           VARCHAR(150) NOT NULL,
    shortcut        VARCHAR(50),
    content_tr      TEXT,
    content_en      TEXT,
    scope           VARCHAR(20)  NOT NULL,
    owner_agent_id  VARCHAR(50)  NOT NULL,
    product_id      BIGINT       REFERENCES products(id) ON DELETE CASCADE,
    visibility      VARCHAR(20)  NOT NULL DEFAULT 'BOTH',
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT chk_canned_scope        CHECK (scope IN ('PERSONAL', 'SHARED')),
    CONSTRAINT chk_canned_visibility   CHECK (visibility IN ('EXTERNAL', 'INTERNAL', 'BOTH')),
    -- En az bir dil varyantı dolu olmalı (boşluk kontrolü servis katmanında).
    CONSTRAINT chk_canned_has_content  CHECK (content_tr IS NOT NULL OR content_en IS NOT NULL),
    -- Kişisel şablonlar ürüne bağlanamaz; ürün yalnız paylaşılan (SHARED) şablonlar içindir.
    CONSTRAINT chk_canned_personal_no_product CHECK (scope = 'SHARED' OR product_id IS NULL)
);

CREATE INDEX IF NOT EXISTS idx_canned_responses_owner   ON canned_responses (owner_agent_id);
CREATE INDEX IF NOT EXISTS idx_canned_responses_scope   ON canned_responses (scope);
CREATE INDEX IF NOT EXISTS idx_canned_responses_product ON canned_responses (product_id);

-- Favoriler: bir kullanıcının ⭐ ile işaretlediği şablonlar. Şablon silinince favori de düşer.
CREATE TABLE IF NOT EXISTS canned_response_favorites (
    user_id            VARCHAR(50) NOT NULL,
    canned_response_id BIGINT      NOT NULL REFERENCES canned_responses(id) ON DELETE CASCADE,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_canned_response_favorites PRIMARY KEY (user_id, canned_response_id)
);

CREATE INDEX IF NOT EXISTS idx_canned_favorites_response ON canned_response_favorites (canned_response_id);
