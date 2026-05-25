package com.ticketsystem.it_service_backend.service;

import com.ticketsystem.it_service_backend.entity.Ticket;
import com.ticketsystem.it_service_backend.entity.TicketAuditLog;
import com.ticketsystem.it_service_backend.repository.TicketAuditLogRepository;
import com.ticketsystem.it_service_backend.websocket.TicketWebSocketEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * Bilet mutation'larında ortak kullanılan audit-log kaydı + WebSocket broadcast'i
 * + reason input doğrulaması. TicketService'i sadeleştirmek için ayrıldı; çoklu
 * mutation servisinin (claim, status, priority, topic, assign) paylaştığı yardımcıdır.
 */
@Log4j2
@Component
@RequiredArgsConstructor
public class TicketAuditHelper {

    private final TicketAuditLogRepository ticketAuditLogRepository;
    private final SimpMessagingTemplate messagingTemplate;

    /**
     * Reason code'suz bir audit kaydı yazar; çoğu otomatik geçiş (örn. CLAIM, CREATE)
     * için kullanılır. {@link #record(Ticket, String, String, String, String, String, String)}
     * çeşidine delege eder.
     *
     * @param ticket audit'lenen bilet
     * @param actorId işlemi yapan kullanıcı ID
     * @param actionType eylem tipi (CREATE / CLAIM / STATUS_CHANGE vs.)
     * @param note serbest not (opsiyonel)
     * @param previousState değişim öncesi durum/değer
     * @param newState değişim sonrası durum/değer
     */
    public void record(Ticket ticket, String actorId, String actionType, String note,
                       String previousState, String newState) {
        record(ticket, actorId, actionType, null, note, previousState, newState);
    }

    /**
     * Audit log satırını yazar ve {@code /topic/tickets/{id}} kanalına bir
     * "ticket updated" WebSocket olayı yayınlar — UI'nin yeniden fetch etmesini
     * tetiklemek için yeterlidir.
     *
     * @param ticket audit'lenen bilet
     * @param actorId işlemi yapan kullanıcı ID
     * @param actionType eylem tipi (RESOLVE, UNCLAIM, STATUS_CHANGE vb.)
     * @param reasonCode sabit sebep kodu (opsiyonel)
     * @param note ek not, OTHER sebebinde zorunlu
     * @param previousState önceki durum
     * @param newState yeni durum
     */
    public void record(Ticket ticket, String actorId, String actionType, String reasonCode,
                       String note, String previousState, String newState) {
        TicketAuditLog auditLog = TicketAuditLog.builder()
                .ticket(ticket)
                .actorId(actorId)
                .actionType(actionType)
                .reasonCode(reasonCode)
                .note(note)
                .previousState(previousState)
                .newState(newState)
                .build();
        ticketAuditLogRepository.save(auditLog);
        broadcastTicketUpdated(ticket.getId());
    }

    /**
     * Sebep input'unu doğrular: kod boş olamaz; "OTHER" seçildiyse not zorunludur.
     *
     * @param reasonCode sebep kodu
     * @param note serbest not (OTHER için zorunlu)
     * @throws ResponseStatusException 400 — kural ihlali durumunda
     */
    public void validateReasonInput(String reasonCode, String note) {
        if (reasonCode == null || reasonCode.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "error.ticket.reason.required");
        }
        if ("OTHER".equals(reasonCode) && (note == null || note.isBlank())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "error.ticket.reason.note.required");
        }
    }

    private void broadcastTicketUpdated(Long ticketId) {
        try {
            messagingTemplate.convertAndSend("/topic/tickets/" + ticketId, TicketWebSocketEvent.ticketUpdated());
        } catch (Exception e) {
            log.warn("WebSocket broadcast hatasi (ticket {}): {}", ticketId, e.getMessage());
        }
    }
}
