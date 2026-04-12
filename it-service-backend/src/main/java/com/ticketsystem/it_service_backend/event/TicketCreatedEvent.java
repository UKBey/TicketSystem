package com.ticketsystem.it_service_backend.event;

import com.ticketsystem.it_service_backend.entity.Ticket;

/**
 * Bilet oluşturulduktan sonra yayınlanan Spring Application Event.
 * 
 * @TransactionalEventListener ile dinlenir ve
 * transaction commit'lendikten SONRA workflow başlatılır.
 * Böylece workflow HTTP çağrısı DB transaction sınırları dışında kalır.
 */
public record TicketCreatedEvent(Ticket ticket) {
}
