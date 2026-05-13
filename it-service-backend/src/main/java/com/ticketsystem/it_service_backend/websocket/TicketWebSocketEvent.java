package com.ticketsystem.it_service_backend.websocket;

public record TicketWebSocketEvent(String type, Object payload) {

    public static TicketWebSocketEvent commentAdded(Object commentDto) {
        return new TicketWebSocketEvent("COMMENT_ADDED", commentDto);
    }

    public static TicketWebSocketEvent attachmentAdded(Object attachmentDto) {
        return new TicketWebSocketEvent("ATTACHMENT_ADDED", attachmentDto);
    }

    public static TicketWebSocketEvent ticketUpdated() {
        return new TicketWebSocketEvent("TICKET_UPDATED", null);
    }
}
