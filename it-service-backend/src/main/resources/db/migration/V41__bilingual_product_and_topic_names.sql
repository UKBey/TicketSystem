-- Ürün ve talep konusu adları iki dilli hale geliyor (canned_responses'taki
-- content_tr/content_en deseniyle aynı): name_tr / name_en, en az biri dolu.
-- Tek dili dolu kayıtlarda diğer dil okuma anında dolu olana düşer (fallback,
-- uygulama katmanında). Eski tek `name` kolonu tamamen kaldırılıyor.
-- Mevcut veri her iki kolona da kopyalanır; iki dilli ad girildikçe ayrışır.

-- 1) products: name -> name_tr + name_en
ALTER TABLE products
    ADD COLUMN IF NOT EXISTS name_tr VARCHAR(255),
    ADD COLUMN IF NOT EXISTS name_en VARCHAR(255);

UPDATE products SET name_tr = name, name_en = name
WHERE name_tr IS NULL AND name_en IS NULL;

ALTER TABLE products DROP COLUMN IF EXISTS name;

ALTER TABLE products
    ADD CONSTRAINT chk_products_name_present CHECK (name_tr IS NOT NULL OR name_en IS NOT NULL);

-- 2) ticket_topics: name -> name_tr + name_en; (product_id, name) tekilliği dil başına ayrışır.
ALTER TABLE ticket_topics
    ADD COLUMN IF NOT EXISTS name_tr VARCHAR(255),
    ADD COLUMN IF NOT EXISTS name_en VARCHAR(255);

UPDATE ticket_topics SET name_tr = name, name_en = name
WHERE name_tr IS NULL AND name_en IS NULL;

ALTER TABLE ticket_topics DROP CONSTRAINT IF EXISTS uq_ticket_topics_product_name;
ALTER TABLE ticket_topics DROP COLUMN IF EXISTS name;

ALTER TABLE ticket_topics
    ADD CONSTRAINT chk_ticket_topics_name_present CHECK (name_tr IS NOT NULL OR name_en IS NOT NULL),
    ADD CONSTRAINT uq_ticket_topics_product_name_tr UNIQUE (product_id, name_tr),
    ADD CONSTRAINT uq_ticket_topics_product_name_en UNIQUE (product_id, name_en);

-- 3) tickets.topic_name_snapshot da iki dile ayrılır: geçmiş biletler her iki
--    dilde de doğru görünsün diye snapshot dil başına tutulur.
ALTER TABLE tickets
    ADD COLUMN IF NOT EXISTS topic_name_snapshot_tr VARCHAR(255),
    ADD COLUMN IF NOT EXISTS topic_name_snapshot_en VARCHAR(255);

UPDATE tickets SET topic_name_snapshot_tr = topic_name_snapshot,
                   topic_name_snapshot_en = topic_name_snapshot
WHERE topic_name_snapshot IS NOT NULL
  AND topic_name_snapshot_tr IS NULL AND topic_name_snapshot_en IS NULL;

ALTER TABLE tickets DROP COLUMN IF EXISTS topic_name_snapshot;
