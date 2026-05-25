package com.ticketsystem.it_service_backend.event;

import com.ticketsystem.it_service_backend.entity.Ticket;

/**
 * Yeni bir bilet veritabanina basariyla yazildiktan sonra yayinlanir.
 *
 * <p>{@code TicketService} {@code save} sonrasi {@code ApplicationEventPublisher}
 * uzerinden publish eder; {@link WorkflowEventListener} bunu
 * {@code AFTER_COMMIT} fazinda yakalayip jBPM workflow instance'ini baslatir.
 *
 * @param ticket yeni olusturulmus bilet
 */
public record TicketCreatedEvent(Ticket ticket) {
}
