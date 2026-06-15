-- Sıkça karşılaşılan sorunlar (known_issues) iki dilli hale geliyor
-- (products/ticket_topics'teki name_tr/name_en deseniyle aynı):
-- title -> title_tr/title_en, content -> content_tr/content_en; her alanda en az biri dolu.
-- Tek dili dolu kayıtlarda diğer dil okuma anında dolu olana düşer (fallback,
-- uygulama katmanında). Eski tek dilli `title`/`content` kolonları kaldırılıyor.
-- Mevcut veri her iki dile de kopyalanır; iki dilli içerik girildikçe ayrışır.

ALTER TABLE known_issues
    ADD COLUMN IF NOT EXISTS title_tr   VARCHAR(255),
    ADD COLUMN IF NOT EXISTS title_en   VARCHAR(255),
    ADD COLUMN IF NOT EXISTS content_tr TEXT,
    ADD COLUMN IF NOT EXISTS content_en TEXT;

UPDATE known_issues SET title_tr = title, title_en = title
WHERE title_tr IS NULL AND title_en IS NULL;

UPDATE known_issues SET content_tr = content, content_en = content
WHERE content_tr IS NULL AND content_en IS NULL;

ALTER TABLE known_issues DROP COLUMN IF EXISTS title;
ALTER TABLE known_issues DROP COLUMN IF EXISTS content;

ALTER TABLE known_issues
    ADD CONSTRAINT chk_known_issues_title_present   CHECK (title_tr   IS NOT NULL OR title_en   IS NOT NULL),
    ADD CONSTRAINT chk_known_issues_content_present CHECK (content_tr IS NOT NULL OR content_en IS NOT NULL);
