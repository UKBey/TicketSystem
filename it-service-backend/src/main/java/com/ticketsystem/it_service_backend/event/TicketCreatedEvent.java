package com.ticketsystem.it_service_backend.event;

import com.ticketsystem.it_service_backend.entity.Ticket;

public record TicketCreatedEvent(Ticket ticket) {
}
