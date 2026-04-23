-- Bu script PostgreSQL container ilk başladığında otomatik çalışır.
-- ticketdb: Zaten POSTGRES_DB env var ile oluşturuluyor.
-- keycloakdb: Keycloak için ayrı veritabanı.

SELECT 'CREATE DATABASE keycloakdb OWNER ticketadmin'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'keycloakdb')\gexec
