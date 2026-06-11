package com.ticketsystem.it_service_backend.service;

import com.ticketsystem.it_service_backend.entity.Comment;
import com.ticketsystem.it_service_backend.entity.Product;
import com.ticketsystem.it_service_backend.entity.Ticket;
import com.ticketsystem.it_service_backend.entity.TicketTopic;
import com.ticketsystem.it_service_backend.repository.TicketTopicRepository;
import com.ticketsystem.it_service_backend.entity.User;
import com.ticketsystem.it_service_backend.event.TicketCreatedEvent;
import com.ticketsystem.it_service_backend.repository.AttachmentRepository;
import com.ticketsystem.it_service_backend.repository.CommentRepository;
import com.ticketsystem.it_service_backend.repository.CsatRepository;
import com.ticketsystem.it_service_backend.repository.TicketClaimRepository;
import com.ticketsystem.it_service_backend.repository.TicketRepository;
import com.ticketsystem.it_service_backend.repository.UserRepository;
import com.ticketsystem.it_service_backend.repository.WorklogRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import com.ticketsystem.it_service_backend.util.AuthRoles;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Core ticket service: creation, single-ticket lookup/authorization and deletion.
 *
 * <p>Holds the shared, widely-consumed surface other services depend on:
 * {@link #getTicketById}/{@link #findById} (no-auth lookups), {@link #getTicketWithAuth}
 * (read authorization) and {@link #validateMutationAccess} (strict mutation
 * authorization). Listing/filtering lives in {@link TicketQueryService}; status,
 * priority and topic mutations live in {@link TicketCommandService}; claim/unclaim and
 * manual assignment are delegated to {@link TicketClaimService}. Notifications and audit
 * log entries are written via {@link NotificationService} and {@link TicketAuditHelper}.
 */
@Log4j2
@Service
@RequiredArgsConstructor
public class TicketService {
    private static final String MSG_USER_NOT_FOUND = "Kullanıcı bulunamadı: ";

    private final TicketRepository ticketRepository;
    private final TicketClaimRepository ticketClaimRepository;
    private final TicketTopicRepository ticketTopicRepository;
    private final CommentRepository commentRepository;
    private final UserRepository userRepository;
    private final WorkflowService workflowService;
    private final SlaPolicyService slaPolicyService;
    private final ApplicationEventPublisher eventPublisher;
    private final CsatRepository csatRepository;
    private final WorklogRepository worklogRepository;
    private final AttachmentRepository attachmentRepository;
    private final NotificationService notificationService;
    private final TicketAuditHelper auditHelper;
    private final TicketClaimService ticketClaimService;

    // -----------------------------------------------------------------
    // Bilet oluşturma
    // -----------------------------------------------------------------

    /**
     * Creates a new ticket.
     *
     * <p>Verifies product access ({@code authorizedProducts}), product active
     * state, topic-to-product matching, and topic active state. The status starts
     * at {@code NEW}, and the SLA deadline is computed from priority and persisted.
     * The opening description is written as the first comment; a notification is
     * sent and a {@link TicketCreatedEvent} is published (an event listener starts
     * the jBPM process).
     *
     * @param ticket ticket payload from the client (productId, topicId, title, etc.)
     * @param customerId ID of the customer opening the ticket (assigned automatically)
     * <p>The topic is required only when the product still has at least one active
     * topic to choose from. When the product has no active topics, the ticket may be
     * created without a topic ("No Topic"); {@code topicId} and the topic name
     * snapshots (tr/en) are left null in that case.
     *
     * @return the persisted ticket
     * @throws ResponseStatusException 400 if topic is missing while active topics exist, or mismatched,
     *                                 403 if the user has no product access,
     *                                 404 if the topic is not found, 422 if product/topic is inactive
     */
    @Transactional
    public Ticket createTicket(Ticket ticket, String customerId) {
        log.info("Yeni bilet oluşturma. Müşteri: {}, Ürün: {}", customerId, ticket.getProductId());

        User customer = userRepository.findById(customerId)
                .orElseThrow(() -> new RuntimeException(MSG_USER_NOT_FOUND + customerId));

        Product product = customer.getAuthorizedProducts().stream()
                .filter(p -> p.getId().equals(ticket.getProductId()))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "error.ticket.create.product.forbidden"));

        if (!Boolean.TRUE.equals(product.getIsActive())) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(422), "error.product.inactive");
        }

        if (ticket.getTopicId() == null) {
            // Konusuz ("No Topic") bilet yalnızca ürünün hiç aktif konusu yoksa açılabilir.
            // Üründe seçilebilecek aktif konu varsa konu zorunludur.
            boolean hasActiveTopics = !ticketTopicRepository
                    .findByProductIdAndIsActiveTrueOrderByIdAsc(product.getId()).isEmpty();
            if (hasActiveTopics) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "error.ticket.topic.required");
            }
            // topicId ve topic ad snapshot'ları (tr/en) null kalır.
        } else {
            TicketTopic topic = ticketTopicRepository.findById(ticket.getTopicId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "error.topic.not.found"));
            if (!topic.getProductId().equals(product.getId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "error.ticket.topic.product.mismatch");
            }
            if (!Boolean.TRUE.equals(topic.getIsActive())) {
                throw new ResponseStatusException(HttpStatusCode.valueOf(422), "error.ticket.topic.inactive");
            }
            ticket.setTopicNameSnapshotTr(topic.getNameTr());
            ticket.setTopicNameSnapshotEn(topic.getNameEn());
        }

        ticket.setCustomerId(customerId);
        ticket.setStatus("NEW");

        // SLA deadline'ı bilet oluşturulurken hemen hesaplanır.
        // Bu sayede scheduler ve getSlaTimerInfo her zaman tutarlı bir deadline'a sahip olur.
        long slaDurationMs = slaPolicyService.getSlaDurationMs(ticket.getPriority());
        ticket.setSlaDeadline(ZonedDateTime.now().plusSeconds(slaDurationMs / 1000));

        Ticket savedTicket = ticketRepository.save(ticket);

        Comment firstComment = Comment.builder()
                .ticket(savedTicket)
                .authorId(customerId)
                .message(savedTicket.getDescription())
                .type("EXTERNAL")
                .build();
        commentRepository.save(firstComment);

        notificationService.notifyTicketCreated(savedTicket);
        eventPublisher.publishEvent(new TicketCreatedEvent(savedTicket));
        auditHelper.record(savedTicket, customerId, "CREATE", null, null, "NEW");

        return savedTicket;
    }

    // -----------------------------------------------------------------
    // Tekil bilet okuma + yetkilendirme
    // -----------------------------------------------------------------

    /**
     * Returns the ticket by ID without authorization checks. Expected to be used
     * only by other services together with their own authorization step.
     *
     * @param id ticket ID
     * @return the ticket
     * @throws RuntimeException if the ticket is not found
     */
    public Ticket getTicketById(Long id) {
        return ticketRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Bilet bulunamadı: " + id));
    }

    /**
     * Returns the ticket by ID as an {@link Optional}, without authorization checks
     * and without throwing on absence. Intended for callers (e.g. the jBPM callback)
     * that need to translate a missing ticket into their own protocol response.
     *
     * @param id ticket ID
     * @return the ticket if present, otherwise empty
     */
    @Transactional(readOnly = true)
    public Optional<Ticket> findById(Long id) {
        return ticketRepository.findById(id);
    }

    /**
     * Idempotently marks the ticket as SLA-breached: sets the flag, persists it and
     * dispatches the breach notification. If the flag is already set (e.g. the jBPM
     * callback is redelivered, or the scheduler marked it first), this is a no-op and
     * no duplicate notification is sent.
     *
     * @param ticket the ticket to mark (may be detached — it is merged on save)
     * @return {@code true} if this call performed the breach, {@code false} if it was
     *         already breached
     */
    @Transactional
    public boolean markSlaBreached(Ticket ticket) {
        // jBPM aynı bilet için callback'i tekrar gönderirse, bayrak zaten set'tir ve
        // mail tekrar gitmemeli. Scheduler de bu bayrağı kontrol ediyor — yani jBPM
        // önce tetiklerse scheduler bir daha denemez ve double-mail riski yoktur.
        if (Boolean.TRUE.equals(ticket.getSlaBreached())) {
            log.info("SLA breach tekrarı atlandı. TicketId={}", ticket.getId());
            return false;
        }

        log.warn("SLA AŞIMI GERÇEKLEŞTİ! TicketId={}", ticket.getId());
        ticket.setSlaBreached(true);
        ticketRepository.save(ticket);
        notificationService.notifySlaBreached(ticket);
        return true;
    }

    /**
     * Returns the ticket after verifying read access. Customers can see only their
     * own tickets; AGENT/ADMIN can see tickets under their authorized products.
     *
     * @param id target ticket ID
     * @param userId requesting user
     * @param roles role list of the user
     * @return the ticket
     * @throws RuntimeException if the ticket is not found
     * @throws ResponseStatusException 403 if read access is missing
     */
    @Transactional(readOnly = true)
    public Ticket getTicketWithAuth(Long id, String userId, List<String> roles) {
        Ticket ticket = getTicketById(id);

        // ADMIN / MANAGER: global görünürlük — tüm ürünlerin biletlerini görür.
        if (AuthRoles.isGlobal(roles)) return ticket;

        // Müşteri yalnızca kendi biletini görür.
        if (userId.equals(ticket.getCustomerId())) return ticket;

        // AGENT / LEAD_AGENT: yalnızca yetkili oldukları ürünlerin biletlerini görür.
        if (AuthRoles.isAgentLevel(roles)) {
            User staff = userRepository.findById(userId).orElseThrow();
            boolean authorized = staff.getAuthorizedProducts().stream()
                    .anyMatch(p -> p.getId().equals(ticket.getProductId()));
            if (authorized) return ticket;
        }

        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "error.ticket.view.forbidden");
    }

    /**
     * Strict authorization check for mutating operations (comment, attachment,
     * worklog, etc.). Every agent — ADMIN included — must hold a claim;
     * customers can mutate only their own tickets.
     *
     * @param id target ticket ID
     * @param userId acting user
     * @param roles role list of the user
     * @return the ticket (when validation passes)
     * @throws ResponseStatusException 403 on authorization/ownership/claim violations
     */
    @Transactional(readOnly = true)
    public Ticket validateMutationAccess(Long id, String userId, List<String> roles) {
        Ticket ticket = getTicketById(id);

        // ADMIN: global — herhangi bir bilette claim olmadan işlem yapabilir.
        if (AuthRoles.isAdmin(roles)) return ticket;

        // LEAD_AGENT: yetkili ürünlerinde claim ALMADAN işlem yapabilir (takım lideri).
        if (AuthRoles.isLeadAgent(roles)) {
            User lead = userRepository.findById(userId).orElseThrow();
            boolean productAuthorized = lead.getAuthorizedProducts().stream()
                    .anyMatch(p -> p.getId().equals(ticket.getProductId()));
            if (productAuthorized) return ticket;
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "error.ticket.view.forbidden");
        }

        // AGENT: yalnızca claim aldığı bilette işlem yapabilir.
        if (roles.contains(AuthRoles.AGENT)) {
            boolean isClaimer = ticketClaimRepository.existsByTicketIdAndAgentId(id, userId);
            if (isClaimer) return ticket;
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "error.ticket.only.claimer.can.act");
        }

        if (roles.contains(AuthRoles.CUSTOMER) && userId.equals(ticket.getCustomerId())) return ticket;

        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "error.forbidden");
    }

    /**
     * Checks whether the agent has claimed the given ticket.
     *
     * @param ticketId ticket ID
     * @param agentId agent ID
     * @return {@code true} when a claim exists
     */
    public boolean isAgentClaimer(Long ticketId, String agentId) {
        return ticketClaimService.isAgentClaimer(ticketId, agentId);
    }

    // -----------------------------------------------------------------
    // Claim & Unclaim — TicketClaimService'e delege edilir
    // -----------------------------------------------------------------

    /**
     * Claims the ticket; delegates to {@link TicketClaimService#claimTicket}.
     *
     * @param id ticket ID
     * @param agentId agent ID
     * @return the updated ticket
     */
    public Ticket claimTicket(Long id, String agentId) {
        return ticketClaimService.claimTicket(id, agentId);
    }

    /**
     * Releases the claim; delegates to {@link TicketClaimService#unclaimTicket(Long, String)}.
     *
     * @param id ticket ID
     * @param agentId agent ID
     * @return the updated ticket
     */
    public Ticket unclaimTicket(Long id, String agentId) {
        return ticketClaimService.unclaimTicket(id, agentId);
    }

    /**
     * Unclaim with a reason; delegates to
     * {@link TicketClaimService#unclaimTicket(Long, String, String, String)}.
     *
     * @param id ticket ID
     * @param agentId agent ID
     * @param reasonCode release reason
     * @param note free-form note (required for OTHER)
     * @return the updated ticket
     */
    public Ticket unclaimTicket(Long id, String agentId, String reasonCode, String note) {
        return ticketClaimService.unclaimTicket(id, agentId, reasonCode, note);
    }

    // -----------------------------------------------------------------
    // Manuel Atama — TicketClaimService'e delege edilir
    // -----------------------------------------------------------------

    /**
     * Manual assignment; delegates to {@link TicketClaimService#assignTicket}.
     *
     * @param ticketId ticket ID
     * @param targetAgentId agent to assign
     * @param adminId acting ADMIN
     * @param note optional description
     * @return the ticket after assignment
     */
    public Ticket assignTicket(Long ticketId, String targetAgentId, String adminId, String note) {
        return ticketClaimService.assignTicket(ticketId, targetAgentId, adminId, note);
    }

    // -----------------------------------------------------------------
    // Silme
    // -----------------------------------------------------------------

    /**
     * Deletes the ticket along with all related records (claim, comment, CSAT,
     * worklog, attachment). If a jBPM process exists it is aborted; errors are
     * logged but do not block deletion.
     *
     * <p>Important: this method is not the final authorization gate — the caller
     * (controller or ProductService cascade) must enforce authorization.
     *
     * @param id ticket ID to delete
     */
    @Transactional
    public void deleteTicket(Long id) {
        log.info("Bilet silme. ID: {}", id);
        try {
            Ticket ticket = getTicketById(id);
            log.warn("AUDIT: Bilet siliniyor. ID: {}", id);
            workflowService.abortTicketWorkflow(ticket);
        } catch (Exception e) {
            log.error("Workflow iptal hatası (bilet silinecek). TicketId={}, Hata={}", id, e.getMessage());
        }

        ticketClaimRepository.deleteByTicketId(id);
        commentRepository.deleteByTicketId(id);
        csatRepository.deleteByTicketId(id);
        worklogRepository.deleteByTicketId(id);
        attachmentRepository.deleteByTicketId(id);
        ticketRepository.deleteById(id);
    }

    // -----------------------------------------------------------------
    // SLA
    // -----------------------------------------------------------------

    /**
     * Loads the ticket from the DB and computes its SLA timer info.
     *
     * @param id ticket ID
     * @return SLA state + remaining time info (see {@link WorkflowService#getSlaTimerInfo})
     * @throws EntityNotFoundException if the ticket is not found
     */
    public Map<String, Object> getSlaTimerInfo(Long id) {
        Ticket ticket = ticketRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Bilet bulunamadı: " + id));
        return workflowService.getSlaTimerInfo(ticket);
    }

    /**
     * Computes SLA timer info for an already-loaded ticket.
     *
     * @param ticket ticket entity
     * @return SLA state + remaining time info
     */
    public Map<String, Object> getSlaTimerInfo(Ticket ticket) {
        return workflowService.getSlaTimerInfo(ticket);
    }
}
