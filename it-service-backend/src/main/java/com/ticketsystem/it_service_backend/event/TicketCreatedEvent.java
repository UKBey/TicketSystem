package com.ticketsystem.it_service_backend.event;

import com.ticketsystem.it_service_backend.entity.Ticket;

/**
 * Published once a new ticket has been successfully written to the database.
 *
 * <p>{@code TicketService} publishes the event through
 * {@code ApplicationEventPublisher} after {@code save};
 * {@link WorkflowEventListener} picks it up in the {@code AFTER_COMMIT}
 * phase and starts the jBPM workflow instance.
 *
 * @param ticket the newly created ticket
 */
public record TicketCreatedEvent(Ticket ticket) {
}
