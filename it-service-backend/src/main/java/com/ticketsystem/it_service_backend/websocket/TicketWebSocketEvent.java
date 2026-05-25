package com.ticketsystem.it_service_backend.websocket;

/**
 * STOMP topic'lerinden ({@code /topic/tickets/{id}}) frontend'e dusen mesajlarin
 * standart zarfi.
 *
 * <p>{@code type} olay turunu (ornek: {@code COMMENT_ADDED}), {@code payload}
 * ise ilgili DTO'yu tasir. Bos payload da gecerlidir — saf "yenile" sinyali
 * icin {@link #ticketUpdated()} kullanilir.
 *
 * @param type    olay turu — frontend bunu switch ile dispatch eder
 * @param payload olaya ozel DTO veya {@code null}
 */
public record TicketWebSocketEvent(String type, Object payload) {

    /**
     * Yeni yorum eklendiginde yayinlanir.
     *
     * @param commentDto yeni eklenen yorumun DTO temsili
     */
    public static TicketWebSocketEvent commentAdded(Object commentDto) {
        return new TicketWebSocketEvent("COMMENT_ADDED", commentDto);
    }

    /**
     * Yeni bir dosya eki yuklendiginde yayinlanir.
     *
     * @param attachmentDto yeni eklenen dosya ekinin DTO temsili
     */
    public static TicketWebSocketEvent attachmentAdded(Object attachmentDto) {
        return new TicketWebSocketEvent("ATTACHMENT_ADDED", attachmentDto);
    }

    /**
     * Bilet durum/atama gibi alanlarinda degisiklik oldugunda yayinlanir; payload
     * tasimaz, frontend taze veri icin yeniden fetch yapar.
     */
    public static TicketWebSocketEvent ticketUpdated() {
        return new TicketWebSocketEvent("TICKET_UPDATED", null);
    }
}
