-- Kullanıcının tercih ettiği tema (light / dark) — mail şablonları bu değere göre renklenir.
ALTER TABLE users
    ADD COLUMN IF NOT EXISTS preferred_theme VARCHAR(10) NOT NULL DEFAULT 'light';
