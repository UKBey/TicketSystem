-- Kullanıcının arayüzde gördüğü tarihlerin tek-tip formatı (tüm tarih gösterimleri
-- bu tercihe göre biçimlenir). Frontend'in tanıdığı preset anahtarı olarak saklanır:
-- DMY_SLASH (31/12/2026), MDY_SLASH (12/31/2026), YMD_DASH (2026-12-31),
-- DMY_DOT (31.12.2026), MED (ay-adlı, dile göre — 31 Ara 2026 / Dec 31, 2026).
ALTER TABLE users
    ADD COLUMN IF NOT EXISTS preferred_date_format VARCHAR(20) NOT NULL DEFAULT 'DMY_SLASH';
