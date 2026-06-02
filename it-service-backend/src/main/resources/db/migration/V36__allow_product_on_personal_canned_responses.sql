-- Kişisel (PERSONAL) hazır yanıtlar da artık bir ürüne bağlanabilir: agent yalnızca
-- kendisinin göreceği, belirli bir ürün için kullanacağı şablon oluşturabilir.
-- V35'teki "kişisel ürün alamaz" kısıtını kaldırıyoruz. product_id hâlâ NULLABLE
-- (ürün bağlamak zorunlu değil; null = tüm ürünlerde görünür/global).
ALTER TABLE canned_responses DROP CONSTRAINT IF EXISTS chk_canned_personal_no_product;
