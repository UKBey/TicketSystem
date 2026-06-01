-- Topicsiz bilet desteği.
-- Bir ürünün hiç aktif talep konusu (ticket_topics.is_active = TRUE) yoksa,
-- müşteri "No Topic" seçerek konusuz bilet açabilir. Bunu mümkün kılmak için
-- tickets.topic_id artık NULL olabilir.
--
-- Mevcut FK kısıtı (fk_ticket_topic ... ON DELETE RESTRICT) korunur; NULL
-- değerler foreign key kısıtını ihlal etmez. Aktif konusu olan ürünler için
-- konu zorunluluğu servis katmanında (TicketService.createTicket) sürdürülür.
ALTER TABLE tickets ALTER COLUMN topic_id DROP NOT NULL;
