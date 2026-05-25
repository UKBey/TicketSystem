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
 * Shared helper used by ticket mutations for audit-log recording, WebSocket
 * broadcasting and reason-input validation. Extracted to keep TicketService lean;
 * shared across multiple mutation services (claim, status, priority, topic, assign).
 */
@Log4j2
@Component
@RequiredArgsConstructor
public class TicketAuditHelper {

    private final TicketAuditLogRepository ticketAuditLogRepository;
    private final SimpMessagingTemplate messagingTemplate;

    /**
     * Records an audit entry without a reason code; used by most automatic
     * transitions (e.g. CLAIM, CREATE). Delegates to the
     * {@link #record(Ticket, String, String, String, String, String, String)} variant.
     *
     * @param ticket the audited ticket
     * @param actorId ID of the acting user
     * @param actionType action type (CREATE / CLAIM / STATUS_CHANGE etc.)
     * @param note free-form note (optional)
     * @param previousState state/value before the change
     * @param newState state/value after the change
     */
    public void record(Ticket ticket, String actorId, String actionType, String note,
                       String previousState, String newState) {
        record(ticket, actorId, actionType, null, note, previousState, newState);
    }

    /**
     * Persists the audit-log row and broadcasts a "ticket updated" WebSocket event
     * to the {@code /topic/tickets/{id}} channel — enough to make the UI refetch.
     *
     * @param ticket the audited ticket
     * @param actorId ID of the acting user
     * @param actionType action type (RESOLVE, UNCLAIM, STATUS_CHANGE etc.)
     * @param reasonCode canonical reason code (optional)
     * @param note additional note; required when the reason is OTHER
     * @param previousState previous state
     * @param newState new state
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
     * Validates reason input: the code must be non-blank, and if "OTHER" is selected
     * a note is required.
     *
     * @param reasonCode reason code
     * @param note free-form note (required for OTHER)
     * @throws ResponseStatusException 400 when the rule is violated
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
