-- Sıkça karşılaşılan sorunlar — bir urune (ve opsiyonel olarak bir topic'e) bagli
-- bilgi tabanı kayitlari. Kullanicilar bir bilet acmadan once burayi gozden gecirir;
-- ileride bilet olusturma akisinda product+topic seciminden sonra otomatik onerilebilir.
CREATE TABLE IF NOT EXISTS known_issues (
    id          BIGSERIAL PRIMARY KEY,
    product_id  BIGINT       NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    topic_id    BIGINT                REFERENCES ticket_topics(id) ON DELETE SET NULL,
    title       VARCHAR(255) NOT NULL,
    content     TEXT         NOT NULL,
    is_active   BOOLEAN      NOT NULL DEFAULT TRUE,
    created_by  VARCHAR(50),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_known_issues_product ON known_issues(product_id);
CREATE INDEX IF NOT EXISTS idx_known_issues_topic   ON known_issues(topic_id);
