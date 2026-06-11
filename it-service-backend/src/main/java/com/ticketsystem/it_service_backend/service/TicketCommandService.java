package com.ticketsystem.it_service_backend.service;

import com.ticketsystem.it_service_backend.entity.Ticket;
import com.ticketsystem.it_service_backend.entity.TicketTopic;
import com.ticketsystem.it_service_backend.entity.User;
import com.ticketsystem.it_service_backend.repository.TicketClaimRepository;
import com.ticketsystem.it_service_backend.repository.TicketRepository;
import com.ticketsystem.it_service_backend.repository.TicketTopicRepository;
import com.ticketsystem.it_service_backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import com.ticketsystem.it_service_backend.util.AuthRoles;
import com.ticketsystem.it_service_backend.util.LocalizedText;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Set;

/**
 * Write-side service for the ticket lifecycle: status, priority and topic mutations.
 *
 * <p>Status transitions are validated against the BPMN state machine
 * ({@link #validateStateTransition}) and role-based authorization
 * ({@link #validateStatusChangePermission}); SLA pause/resume is mirrored to the jBPM
 * side through {@link WorkflowService}. Shared ticket lookup and read-authorization are
 * delegated to {@link TicketService} ({@code getTicketById} / {@code getTicketWithAuth}).
 * Notifications and audit log entries are written on every successful operation via
 * {@link NotificationService} and {@link TicketAuditHelper}.
 */
@Log4j2
@Service
@RequiredArgsConstructor
public class TicketCommandService {
    private static final String ST_RESOLVED = "RESOLVED";
    private static final String ST_WAITING = "WAITING_FOR_CUSTOMER";
    private static final String ST_IN_PROGRESS = "IN_PROGRESS";
    private static final String ST_CLOSED = "CLOSED";

    // Durum makinesi BPMN'de (ticket-lifecycle.bpmn2) yaşar: her statü explicit bir
    // wait node, geçişler `transition_<TARGET>` signal'leri ile tetiklenir ve geçerlilik
    // BPMN şemasıyla encode edilmiştir. Java tarafında geçiş tablosu YOK — geçişin
    // valid mi olduğunu BPMN belirler. {@link #validateStateTransition} BPMN'i signal
    // edip process variable'ı geri okuyarak senkron olarak doğrular; BPMN reddederse
    // (state node signal'i dinlemiyorsa) 400 fırlatılır.
    private static final Set<String> SLA_PAUSED_STATES = Set.of(ST_WAITING, ST_RESOLVED);
    private static final Set<String> SLA_ACTIVE_STATES = Set.of("NEW", ST_IN_PROGRESS);

    private final TicketRepository ticketRepository;
    private final TicketClaimRepository ticketClaimRepository;
    private final TicketTopicRepository ticketTopicRepository;
    private final UserRepository userRepository;
    private final WorkflowService workflowService;
    private final SlaPolicyService slaPolicyService;
    private final NotificationService notificationService;
    private final TicketAuditHelper auditHelper;
    private final TicketService ticketService;

    /**
     * Closes the ticket; the reason code and optional note are written to the audit log.
     *
     * <p>The state machine and role-based authorization are validated, the reason
     * input is checked, SLA pause/resume is applied if needed, and the jBPM
     * process is terminated via the {@code ticket_closed} signal.
     *
     * @param id ticket ID
     * @param reasonCode close reason (required)
     * @param note explanatory note (required when reason is OTHER)
     * @param userId acting user
     * @param roles role list of the user
     * @return the closed ticket
     * @throws ResponseStatusException 400 on status/reason, 403 on authorization
     */
    @Transactional
    public Ticket closeTicket(Long id, String reasonCode, String note, String userId, List<String> roles) {
        log.info("Close isteği. Bilet: {}, Kullanıcı: {}, Sebep: {}", id, userId, reasonCode);
        Ticket ticket = ticketService.getTicketById(id);
        String oldStatus = ticket.getStatus();

        validateStateTransition(ticket, oldStatus, ST_CLOSED);
        validateStatusChangePermission(ticket, oldStatus, ST_CLOSED, userId, roles);
        validateReasonInput(reasonCode, note);

        applyStatusSpecificRules(ticket, oldStatus, ST_CLOSED, userId);

        ticket.setStatus(ST_CLOSED);
        ticket.setClosedAt(ZonedDateTime.now());

        Ticket saved = ticketRepository.save(ticket);
        handleWorkflowSignals(saved, oldStatus, ST_CLOSED);
        notificationService.notifyStatusChanged(saved, oldStatus);
        recordTicketAuditLog(saved, userId, "CLOSE", reasonCode, note, oldStatus, saved.getStatus());

        return saved;
    }

    // -----------------------------------------------------------------
    // Durum güncellemesi
    // -----------------------------------------------------------------

    /**
     * Updates the ticket status. CLOSED targets are delegated to {@link #closeTicket};
     * RESOLVED requires a reason input.
     *
     * <p>State-machine validation, role/ownership checks, SLA pause/resume
     * orchestration, notifications and audit recording all run inside a single
     * transaction. The audit action type (RESOLVE, REOPEN, WAITING, RESUME,
     * STATUS_CHANGE) is determined automatically.
     *
     * @param id ticket ID
     * @param newStatus new status
     * @param reasonCode reason code (required for RESOLVED)
     * @param note additional note (required when reason is OTHER)
     * @param userId acting user
     * @param roles role list of the user
     * @return the updated ticket
     * @throws ResponseStatusException 400 on status/reason, 403 on authorization
     */
    @Transactional
    public Ticket updateTicketStatus(Long id, String newStatus, String reasonCode, String note,
                                     String userId, List<String> roles) {
        log.info("Statü güncelleme. Bilet: {}, Yeni: {}, Kullanıcı: {}, Sebep: {}", id, newStatus, userId, reasonCode);
        if (ST_CLOSED.equals(newStatus)) {
            return closeTicket(id, reasonCode, note, userId, roles);
        }

        Ticket ticket = ticketService.getTicketById(id);
        String oldStatus = ticket.getStatus();

        validateStateTransition(ticket, oldStatus, newStatus);
        validateStatusChangePermission(ticket, oldStatus, newStatus, userId, roles);
        if (ST_RESOLVED.equals(newStatus)) {
            validateReasonInput(reasonCode, note);
        }

        applyStatusSpecificRules(ticket, oldStatus, newStatus, userId);

        ticket.setStatus(newStatus);
        if (ST_RESOLVED.equals(newStatus)) ticket.setResolvedAt(ZonedDateTime.now());
        else if (ST_CLOSED.equals(newStatus))  ticket.setClosedAt(ZonedDateTime.now());

        Ticket saved = ticketRepository.save(ticket);
        handleWorkflowSignals(saved, oldStatus, newStatus);

        if (ST_RESOLVED.equals(newStatus)) notificationService.notifyTicketResolved(saved);
        else                               notificationService.notifyStatusChanged(saved, oldStatus);

        String actionType;
        if (ST_RESOLVED.equals(newStatus)) actionType = "RESOLVE";
        else if (ST_IN_PROGRESS.equals(newStatus) && ST_RESOLVED.equals(oldStatus)) actionType = "REOPEN";
        else if (ST_WAITING.equals(newStatus)) actionType = "WAITING";
        else if (ST_IN_PROGRESS.equals(newStatus) && ST_WAITING.equals(oldStatus)) actionType = "RESUME";
        else actionType = "STATUS_CHANGE";
        recordTicketAuditLog(saved, userId, actionType, reasonCode, note, oldStatus, newStatus);

        return saved;
    }

    /**
     * Updates the ticket priority. If the SLA counter is active, it is paused first
     * so the elapsed time accumulates correctly; {@code slaDeadline} is then
     * recomputed against the new duration. When the SLA is paused, the jBPM timer
     * is untouched and only the DB deadline is refreshed.
     *
     * @param id ticket ID
     * @param newPriority new priority (LOW/MEDIUM/HIGH/CRITICAL)
     * @param reasonCode reason code
     * @param note explanation
     * @param userId acting user
     * @param roles role list of the user
     * @return the updated ticket
     * @throws ResponseStatusException 400 on invalid priority, 403 on authorization
     */
    @Transactional
    public Ticket updateTicketPriority(Long id, String newPriority, String reasonCode, String note,
                                       String userId, List<String> roles) {
        log.info("Öncelik güncelleme. Bilet: {}, Yeni Öncelik: {}, Kullanıcı: {}", id, newPriority, userId);
        List<String> valid = List.of("LOW", "MEDIUM", "HIGH", "CRITICAL");
        if (!valid.contains(newPriority)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "error.ticket.invalid.priority");
        }

        Ticket ticket = ticketService.getTicketWithAuth(id, userId, roles);
        String oldPriority = ticket.getPriority();
        if (oldPriority.equals(newPriority)) return ticket;

        boolean isSlaActive = !Boolean.TRUE.equals(ticket.getSlaBreached())
                && !ST_CLOSED.equals(ticket.getStatus());
        boolean isPaused    = ticket.getSlaPausedAt() != null
                || ST_WAITING.equals(ticket.getStatus())
                || ST_RESOLVED.equals(ticket.getStatus());

        // Aktif sayaç varsa önce dondur; elapsed süre doğru biriksin.
        if (isSlaActive && !isPaused) {
            workflowService.pauseSla(ticket);
        }

        ticket.setPriority(newPriority);

        if (isSlaActive) {
            long newDurationMs = slaPolicyService.getSlaDurationMs(newPriority);
            long accumulated   = ticket.getSlaElapsedMs() != null ? ticket.getSlaElapsedMs() : 0L;

            if (!isPaused) {
                // resumeSla ticket.getPriority() okur — priority zaten güncellendi.
                // Kalan süreyi jBPM timer'ına resume_sla sinyaliyle iletir.
                workflowService.resumeSla(ticket);
                long remaining = Math.max(0L, newDurationMs - accumulated);
                ticket.setSlaDeadline(ZonedDateTime.now().plusSeconds(remaining / 1000));
            } else {
                // Duraklatılmış: jBPM timer zaten durmuş.
                // DB'deki deadline'ı yeni süreye göre ayarla; resumeSla
                // normal akışta yeni priority'yi zaten okuyacak.
                if (ticket.getCreatedAt() != null) {
                    ticket.setSlaDeadline(ticket.getCreatedAt().plusSeconds(newDurationMs / 1000));
                }
            }
        }

        Ticket saved = ticketRepository.save(ticket);
        recordTicketAuditLog(saved, userId, "PRIORITY_CHANGE", reasonCode, note, oldPriority, newPriority);
        return saved;
    }

    /**
     * Changes the ticket topic. The new topic must belong to the same product and
     * be active. The previous and new topic names are written to the audit log.
     *
     * @param id ticket ID
     * @param newTopicId new topic ID (required)
     * @param reasonCode reason code
     * @param note explanation
     * @param userId acting user
     * @param roles role list of the user
     * @return the updated ticket
     * @throws ResponseStatusException 400 if topic is missing/mismatched/inactive,
     *                                 404 if the topic is not found,
     *                                 403 on authorization
     */
    @Transactional
    public Ticket updateTicketTopic(Long id, Long newTopicId, String reasonCode, String note,
                                    String userId, List<String> roles) {
        log.info("Konu güncelleme. Bilet: {}, Yeni Topic: {}, Kullanıcı: {}", id, newTopicId, userId);
        if (newTopicId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "error.ticket.topic.required");
        }

        Ticket ticket = ticketService.getTicketWithAuth(id, userId, roles);
        Long oldTopicId = ticket.getTopicId();
        if (newTopicId.equals(oldTopicId)) return ticket;

        TicketTopic newTopic = ticketTopicRepository.findById(newTopicId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "error.ticket.topic.notfound"));

        if (!newTopic.getProductId().equals(ticket.getProductId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "error.ticket.topic.product.mismatch");
        }
        if (Boolean.FALSE.equals(newTopic.getIsActive())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "error.ticket.topic.inactive");
        }

        // Audit kaydı dil-bağımsız kalıcı bir metin ister; iki ad farklıysa "tr / en" yazılır.
        String oldTopicName = oldTopicId != null
                ? ticketTopicRepository.findById(oldTopicId)
                        .map(t -> LocalizedText.label(t.getNameTr(), t.getNameEn()))
                        .orElse(String.valueOf(oldTopicId))
                : null;

        ticket.setTopicId(newTopicId);
        // Keep the denormalized name snapshots in sync so the response (and every later
        // read) shows the topic name instead of falling back to "#<id>".
        ticket.setTopicNameSnapshotTr(newTopic.getNameTr());
        ticket.setTopicNameSnapshotEn(newTopic.getNameEn());
        Ticket saved = ticketRepository.save(ticket);
        recordTicketAuditLog(saved, userId, "TOPIC_CHANGE", reasonCode, note, oldTopicName,
                LocalizedText.label(newTopic.getNameTr(), newTopic.getNameEn()));
        return saved;
    }

    private void recordTicketAuditLog(Ticket ticket, String actorId, String actionType, String reasonCode,
                                      String note, String previousState, String newState) {
        auditHelper.record(ticket, actorId, actionType, reasonCode, note, previousState, newState);
    }

    private void validateReasonInput(String reasonCode, String note) {
        auditHelper.validateReasonInput(reasonCode, note);
    }

    /**
     * Asks the BPMN state machine whether the transition from {@code current} to
     * {@code next} is allowed for the given ticket. We send the corresponding
     * {@code transition_<TARGET>} signal; the engine only advances the process
     * variable if the source state listens for that signal, so a synchronous
     * read-back of the {@code status} variable tells us yes or no.
     *
     * <p>BPMN is the single source of truth for valid transitions — there is no
     * Java map to keep in sync with the diagram. If KIE Server is unreachable
     * the verify step returns false and we surface that as a 400; the caller
     * can retry once the workflow engine is healthy.
     */
    private void validateStateTransition(Ticket ticket, String current, String next) {
        if (next == null || next.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "error.ticket.unknown.status");
        }
        if (next.equals(current)) {
            // No-op transition; nothing to ask the BPMN.
            return;
        }
        if (ticket.getProcessInstanceId() == null) {
            // Workflow never started for this ticket (e.g. legacy data, or a unit
            // test that bypassed createTicket). No BPMN to consult — accept the
            // transition since the state machine isn't running. Production tickets
            // always go through createTicket which starts the workflow.
            log.warn("Ticket'in processInstanceId'si yok, BPMN state machine atlandı. TicketId={}",
                    ticket.getId());
            return;
        }

        workflowService.requestStatusTransition(ticket, next);
        if (workflowService.verifyTransitionApplied(ticket, next)) {
            return;
        }

        // Transition not confirmed. If the BPMN process instance no longer exists
        // (stale processInstanceId — e.g. the jBPM history store was reset while the
        // ticket survived in ticketdb), there is no state machine left to consult.
        // Treat it like the "no workflow" case above and accept the DB-side
        // transition instead of blocking the ticket forever.
        if (workflowService.isProcessInstanceMissing(ticket)) {
            log.warn("BPMN süreç örneği KIE'de yok (eski processInstanceId) — DB geçişi kabul ediliyor. "
                    + "TicketId={}, ProcessInstanceId={}, {} -> {}",
                    ticket.getId(), ticket.getProcessInstanceId(), current, next);
            return;
        }

        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "error.ticket.invalid.status.transition");
    }

    private void validateStatusChangePermission(Ticket ticket, String oldStatus, String newStatus,
                                                 String userId, List<String> roles) {
        // ADMIN: global — herhangi bir bilette claim olmadan statü değiştirebilir.
        if (AuthRoles.isAdmin(roles)) {
            return;
        }

        // LEAD_AGENT: yetkili ürünlerinde claim ALMADAN statü değiştirebilir (takım lideri).
        if (AuthRoles.isLeadAgent(roles)) {
            User lead = userRepository.findById(userId).orElseThrow();
            boolean productAuthorized = lead.getAuthorizedProducts().stream()
                    .anyMatch(p -> p.getId().equals(ticket.getProductId()));
            if (!productAuthorized) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "error.ticket.view.forbidden");
            }
            return;
        }

        if (roles.contains(AuthRoles.CUSTOMER)) {
            if (!userId.equals(ticket.getCustomerId())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "error.ticket.status.own.only");
            }
            boolean allowed =
                    (ST_WAITING.equals(oldStatus) && ST_IN_PROGRESS.equals(newStatus)) ||
                    (ST_RESOLVED.equals(oldStatus) && ST_IN_PROGRESS.equals(newStatus)) ||
                    (ST_RESOLVED.equals(oldStatus) && ST_CLOSED.equals(newStatus));
            if (!allowed) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "error.ticket.status.customer.transition");
            }
            return;
        }

        if (roles.contains(AuthRoles.AGENT)) {
            boolean hasClaim = ticketClaimRepository.existsByTicketIdAndAgentId(ticket.getId(), userId);
            if (!hasClaim) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "error.ticket.status.requires.claim");
            }
            return;
        }

        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "error.forbidden");
    }

    /**
     * IN_PROGRESS → NEW transition: every claim is cleared and the ticket returns
     * to the pool. Because this transition requires ADMIN authority, regular
     * unclaim should go through DELETE /api/v1/tickets/{id}/claim instead.
     */
    private void applyStatusSpecificRules(Ticket ticket, String oldStatus, String newStatus, String userId) {
        if (ST_IN_PROGRESS.equals(oldStatus) && "NEW".equals(newStatus)) {
            log.warn("AUDIT: Tüm claim'ler temizleniyor. Bilet: {}, İşlemi yapan: {}", ticket.getId(), userId);
            ticketClaimRepository.deleteByTicketId(ticket.getId());
        }

        if (ST_IN_PROGRESS.equals(oldStatus) && ST_CLOSED.equals(newStatus)) {
            log.warn("AUDIT: Ajan müşteri yanıtı beklerken bileti kapattı. Bilet: {}, Ajan: {}",
                    ticket.getId(), userId);
        }
    }

    private void handleWorkflowSignals(Ticket ticket, String oldStatus, String newStatus) {
        // Statü geçişi sinyali ve doğrulaması {@link #validateStateTransition} içinde
        // zaten yapıldı — burada sadece SLA timer'ı için yan etkiler ve close
        // sürecinin sonlandırması var.
        try {
            if (SLA_ACTIVE_STATES.contains(oldStatus) && SLA_PAUSED_STATES.contains(newStatus)) {
                workflowService.pauseSla(ticket);
                ticketRepository.save(ticket);
            }
            if (SLA_PAUSED_STATES.contains(oldStatus) && SLA_ACTIVE_STATES.contains(newStatus)) {
                workflowService.resumeSla(ticket);
                ticketRepository.save(ticket);
            }
            if (ST_CLOSED.equals(newStatus)) {
                // Authoritative state branch'in terminate end'i tüm süreci sonlandırır;
                // legacy SLA branch için ticket_closed sinyali de hâlâ atılır (geriye
                // uyumlu — paralel kol bağımsız olarak biter).
                workflowService.closeTicketWorkflow(ticket);
            }
        } catch (Exception e) {
            log.error("Workflow sinyal hatası. TicketId={}, Geçiş={} → {}, Hata={}",
                    ticket.getId(), oldStatus, newStatus, e.getMessage());
        }
    }
}
