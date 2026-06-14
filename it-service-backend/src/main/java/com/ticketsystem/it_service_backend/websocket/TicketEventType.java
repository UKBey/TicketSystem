package com.ticketsystem.it_service_backend.websocket;

/**
 * Kind of {@link TicketWebSocketEvent} pushed to the frontend over a STOMP topic
 * ({@code /topic/tickets/{id}}). The frontend dispatches on this value.
 *
 * <p>Not persisted — serialized to its constant name (e.g. {@code COMMENT_ADDED}) by
 * Jackson, which is exactly the wire contract the client already switches on.
 */
public enum TicketEventType {
    /** A new comment was added to the ticket. */
    COMMENT_ADDED,
    /** A new attachment was uploaded to the ticket. */
    ATTACHMENT_ADDED,
    /** Ticket fields (status, assignment, …) changed — the client re-fetches. */
    TICKET_UPDATED
}
