-- Kullanıcı dil tercihi alanı ekleniyor.
-- Desteklenen değerler: 'en' (varsayılan), 'tr'
ALTER TABLE users
    ADD COLUMN IF NOT EXISTS preferred_language VARCHAR(5) NOT NULL DEFAULT 'en';
