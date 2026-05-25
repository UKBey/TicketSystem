package com.ticketsystem.it_service_backend.websocket;

/**
 * Standard envelope for messages pushed to the frontend from STOMP topics
 * ({@code /topic/tickets/{id}}).
 *
 * <p>{@code type} carries the event kind (for example
 * {@code COMMENT_ADDED}) and {@code payload} carries the relevant DTO. An
 * empty payload is also valid — {@link #ticketUpdated()} is used for a pure
 * "refresh" signal.
 *
 * @param type    event kind — the frontend dispatches this with a switch
 * @param payload event-specific DTO, or {@code null}
 */
public record TicketWebSocketEvent(String type, Object payload) {

    /**
     * Published when a new comment is added.
     *
     * @param commentDto DTO representation of the newly added comment
     */
    public static TicketWebSocketEvent commentAdded(Object commentDto) {
        return new TicketWebSocketEvent("COMMENT_ADDED", commentDto);
    }

    /**
     * Published when a new attachment is uploaded.
     *
     * @param attachmentDto DTO representation of the newly added attachment
     */
    public static TicketWebSocketEvent attachmentAdded(Object attachmentDto) {
        return new TicketWebSocketEvent("ATTACHMENT_ADDED", attachmentDto);
    }

    /**
     * Published when fields such as ticket status or assignment change;
     * carries no payload and the frontend re-fetches for fresh data.
     */
    public static TicketWebSocketEvent ticketUpdated() {
        return new TicketWebSocketEvent("TICKET_UPDATED", null);
    }
}
