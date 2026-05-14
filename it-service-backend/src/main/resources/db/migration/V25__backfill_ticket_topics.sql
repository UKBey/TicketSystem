-- Eski biletleri yeni "konu" mekanizmasıyla uyumlu hale getirir.
-- 1) Her ürün için "Diğer" konusunu garanti altına al (yoksa oluştur).
-- 2) Konusuz biletleri (topic_id IS NULL) ilgili ürünün "Diğer" konusuna bağla.
-- 3) topic_name_snapshot'ı doldur.
-- 4) tickets.topic_id artık zorunlu (NOT NULL).

INSERT INTO ticket_topics (product_id, name, is_active)
SELECT p.id, 'Diğer', TRUE
FROM products p
WHERE NOT EXISTS (
    SELECT 1 FROM ticket_topics tt
    WHERE tt.product_id = p.id AND tt.name = 'Diğer'
);

UPDATE tickets t
SET topic_id = (
        SELECT tt.id FROM ticket_topics tt
        WHERE tt.product_id = t.product_id AND tt.name = 'Diğer'
        LIMIT 1
    ),
    topic_name_snapshot = 'Diğer'
WHERE t.topic_id IS NULL
  AND t.product_id IS NOT NULL;

-- topic_id hala NULL olan bilet kalmışsa (örn. product_id NULL veya backfill başarısızsa) durdur.
DO $$
DECLARE
    missing_count INT;
BEGIN
    SELECT COUNT(*) INTO missing_count FROM tickets WHERE topic_id IS NULL;
    IF missing_count > 0 THEN
        RAISE EXCEPTION 'V25 backfill: % bilet hala konusuz. NOT NULL kısıtını ekleyemiyorum.', missing_count;
    END IF;
END $$;

ALTER TABLE tickets
    ALTER COLUMN topic_id SET NOT NULL;
