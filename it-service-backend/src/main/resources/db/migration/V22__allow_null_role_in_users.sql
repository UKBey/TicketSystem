-- Rol ataması başarısız olan kullanıcıların DB'ye kaydedilebilmesi için
-- role sütunundaki NOT NULL kısıtı kaldırılıyor.
-- Admin, Edit Role özelliği ile bu kullanıcılara sonradan rol atayabilir.
ALTER TABLE users ALTER COLUMN role DROP NOT NULL;
