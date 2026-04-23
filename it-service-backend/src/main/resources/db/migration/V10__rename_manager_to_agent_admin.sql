-- V10: Mevcut MANAGER kullanıcılarını AGENT_ADMIN'a dönüştür
-- Bu migration ile role tablosundaki mevcut MANAGER kayıtları AGENT_ADMIN'a taşınır.

UPDATE users 
SET role = 'AGENT_ADMIN' 
WHERE role = 'MANAGER';
