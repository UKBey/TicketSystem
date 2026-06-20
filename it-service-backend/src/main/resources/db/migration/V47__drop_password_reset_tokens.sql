-- V47: Özel "şifremi unuttum" akışı kaldırıldı; parola sıfırlama artık tamamen
-- Keycloak'ın native reset-credentials akışıyla yapılıyor. Backend token tablosu
-- (V29) ve ona bağlı index'ler artık kullanılmıyor — tabloyu düşürüyoruz.
-- Index'ler tablo ile birlikte otomatik düşer.

DROP TABLE IF EXISTS password_reset_tokens;
