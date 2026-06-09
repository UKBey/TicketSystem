-- Kullanıcının PDF dışa aktarma modalında en son kullandığı tercihler (hangi bölümler
-- seçili, PDF dili, PDF teması) — frontend'in tanımladığı opak bir JSON string olarak
-- saklanır. Nullable: değer yoksa kullanıcı henüz bir PDF üretmemiştir (varsayılanlar uygulanır).
ALTER TABLE users
    ADD COLUMN IF NOT EXISTS pdf_export_preferences VARCHAR(2000);
