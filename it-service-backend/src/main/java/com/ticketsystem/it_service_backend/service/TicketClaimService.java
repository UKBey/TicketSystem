package com.ticketsystem.it_service_backend.service;

import com.ticketsystem.it_service_backend.entity.AgentProductLimit;
import com.ticketsystem.it_service_backend.entity.Product;
import com.ticketsystem.it_service_backend.entity.Ticket;
import com.ticketsystem.it_service_backend.entity.TicketClaim;
import com.ticketsystem.it_service_backend.entity.User;
import com.ticketsystem.it_service_backend.exception.TicketLimitExceededException;
import com.ticketsystem.it_service_backend.util.AuthRoles;
import com.ticketsystem.it_service_backend.repository.AgentProductLimitRepository;
import com.ticketsystem.it_service_backend.repository.ProductRepository;
import com.ticketsystem.it_service_backend.repository.TicketClaimRepository;
import com.ticketsystem.it_service_backend.repository.TicketRepository;
import com.ticketsystem.it_service_backend.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Business rules for claim, unclaim and manual assignment. Kept in a separate
 * class to stop TicketService from growing unbounded — TicketService preserves
 * the public API and delegates to this service.
 *
 * <p>Reads tickets via TicketRepository directly, which breaks the circular
 * dependency that would arise from depending on TicketService.
 */
@Log4j2
@Service
@RequiredArgsConstructor
public class TicketClaimService {
    private static final String ST_IN_PROGRESS = "IN_PROGRESS";
    private static final String SYNC_ERR_FMT = "Workflow sync hatası. TicketId={}, Hata={}";

    private final TicketRepository ticketRepository;
    private final TicketClaimRepository ticketClaimRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final AgentProductLimitRepository agentProductLimitRepository;
    private final WorkflowService workflowService;
    private final NotificationService notificationService;
    private final TicketAuditHelper auditHelper;

    /**
     * Has an agent claim a ticket. Any status except CLOSED is claimable.
     * On NEW (the first claim) the ticket moves to IN_PROGRESS; in other statuses
     * the status is preserved and a new claim is added alongside any existing ones.
     * Product authorization and the effective limit (product default + custom
     * override) are both checked.
     *
     * @param id ticket ID to claim
     * @param agentId acting agent
     * @return the updated ticket
     * @throws ResponseStatusException 400 on status, 403 on product access, 409 if already claimed
     * @throws TicketLimitExceededException if the agent's limit is full
     */
    @Transactional
    public Ticket claimTicket(Long id, String agentId) {
        log.info("Claim isteği. Bilet: {}, Ajan: {}", id, agentId);
        Ticket ticket = loadTicket(id);

        String currentStatus = ticket.getStatus();
        // Kapalı bilet dışında her statüdeki bilet claim alınabilir (manuel assign ile tutarlı).
        if ("CLOSED".equals(currentStatus)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "error.ticket.claim.invalid.status");
        }

        User agent = userRepository.findById(agentId)
                .orElseThrow(() -> new RuntimeException("Kullanıcı bulunamadı: " + agentId));

        // ADMIN/MANAGER (global) tüm ürünlere erişir → ürün-yetki kontrolünü atlar; böylece
        // gördükleri tüm havuzdan claim alabilirler. AGENT/LEAD_AGENT ürünleriyle sınırlıdır.
        boolean isAuthorized = AuthRoles.isGlobal(agent.getRoles())
                || agent.getAuthorizedProducts().stream()
                        .anyMatch(p -> p.getId().equals(ticket.getProductId()));
        if (!isAuthorized) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "error.ticket.claim.product.forbidden");
        }

        Product product = productRepository.findById(ticket.getProductId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "error.product.not.found"));

        // #3: ADMIN/MANAGER (global rol) için bilet limiti uygulanmaz — kapasite sınırsızdır.
        Integer effectiveLimit = AuthRoles.isGlobal(agent.getRoles()) ? null : resolveEffectiveLimit(agentId, product);
        if (effectiveLimit != null) {
            long activeCount = ticketClaimRepository.countActiveTicketsByAgentAndProduct(agentId, product.getId());
            if (activeCount >= effectiveLimit) {
                throw new TicketLimitExceededException("error.ticket.limit.exceeded", effectiveLimit);
            }
        }

        if (ticketClaimRepository.existsByTicketIdAndAgentId(id, agentId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "error.ticket.already.claimed");
        }

        TicketClaim claim = TicketClaim.builder()
                .ticket(ticket)
                .agentId(agentId)
                .build();
        ticketClaimRepository.save(claim);

        // İlk claim ise bileti IN_PROGRESS'e taşır.
        if ("NEW".equals(currentStatus)) {
            ticket.setStatus(ST_IN_PROGRESS);
            ticketRepository.save(ticket);
            log.info("İlk claim — bilet IN_PROGRESS'e alındı. Bilet: {}", id);
            try {
                workflowService.syncTicketAssignment(ticket, agentId);
            } catch (Exception e) {
                log.error(SYNC_ERR_FMT, id, e.getMessage());
            }
        }

        notificationService.notifyTicketClaimed(ticket, agentId);
        auditHelper.record(ticket, agentId, "CLAIM", null, currentStatus, ticket.getStatus());
        return ticket;
    }

    /**
     * Releases the agent's own claim. If this was the last claim, the ticket
     * returns to NEW.
     *
     * @param id ticket ID
     * @param agentId acting agent
     * @return the updated ticket
     */
    @Transactional
    public Ticket unclaimTicket(Long id, String agentId) {
        return unclaimTicket(id, agentId, null, null);
    }

    /**
     * Releases the agent's own claim and writes the reason code and optional note
     * to the audit log. When the last claim is released, the ticket returns to
     * the NEW pool and the jBPM side is synchronized.
     *
     * @param id ticket ID
     * @param agentId acting agent
     * @param reasonCode release reason (must be non-blank)
     * @param note free-form note (required when reason is OTHER)
     * @return the updated ticket
     * @throws ResponseStatusException 400 if the agent has no active claim or the reason is invalid
     */
    @Transactional
    public Ticket unclaimTicket(Long id, String agentId, String reasonCode, String note) {
        log.info("Unclaim isteği. Bilet: {}, Ajan: {}, Sebep: {}", id, agentId, reasonCode);
        Ticket ticket = loadTicket(id);
        String previousStatus = ticket.getStatus();

        if (!ticketClaimRepository.existsByTicketIdAndAgentId(id, agentId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "error.ticket.no.active.claim");
        }
        auditHelper.validateReasonInput(reasonCode, note);

        ticketClaimRepository.deleteByTicketIdAndAgentId(id, agentId);

        long remaining = ticketClaimRepository.countByTicketId(id);
        if (remaining == 0 && ST_IN_PROGRESS.equals(ticket.getStatus())) {
            log.info("Son claim bırakıldı — bilet havuza (NEW) geri dönüyor. Bilet: {}", id);
            ticket.setStatus("NEW");
            ticketRepository.save(ticket);
            try {
                workflowService.syncTicketStatus(ticket);
            } catch (Exception e) {
                log.error(SYNC_ERR_FMT, id, e.getMessage());
            }
        }

        auditHelper.record(ticket, agentId, "UNCLAIM", reasonCode, note, previousStatus, ticket.getStatus());
        return ticket;
    }

    /**
     * Manually assigns a ticket to the target agent on behalf of an Agent Admin.
     *
     * <p>Product authorization is verified for both the admin and the target agent,
     * and the effective limit is checked. On a NEW ticket the first assignment
     * moves the status to IN_PROGRESS. When the target agent already holds a claim,
     * the operation ends idempotently (the existing ticket is returned).
     *
     * @param ticketId target ticket ID
     * @param targetAgentId agent to assign
     * @param adminId ADMIN making the assignment
     * @param note optional description (written to the audit log)
     * @return the ticket after assignment
     * @throws ResponseStatusException 400 on CLOSED/limit, 403 on authorization
     * @throws EntityNotFoundException if admin/agent/product is not found
     */
    @Transactional
    public Ticket assignTicket(Long ticketId, String targetAgentId, String adminId, String note) {
        log.info("Manuel atama isteği. Bilet: {}, Hedef Agent: {}, Admin: {}", ticketId, targetAgentId, adminId);

        Ticket ticket = loadTicket(ticketId);

        if ("CLOSED".equals(ticket.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "error.ticket.assign.closed");
        }

        User adminUser = userRepository.findById(adminId)
                .orElseThrow(() -> new EntityNotFoundException("Admin bulunamadı: " + adminId));
        // ADMIN global atayabilir; LEAD_AGENT yalnızca yetkili olduğu ürünlerde atayabilir.
        boolean assignerIsAdmin = adminUser.getRoles() != null
                && adminUser.getRoles().contains(com.ticketsystem.it_service_backend.util.AuthRoles.ADMIN);
        if (!assignerIsAdmin) {
            boolean adminAuthorized = adminUser.getAuthorizedProducts().stream()
                    .anyMatch(p -> p.getId().equals(ticket.getProductId()));
            if (!adminAuthorized) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "error.ticket.assign.admin.not.authorized");
            }
        }

        User targetAgent = userRepository.findById(targetAgentId)
                .orElseThrow(() -> new EntityNotFoundException("Agent bulunamadı: " + targetAgentId));

        boolean isAuthorized = targetAgent.getAuthorizedProducts().stream()
                .anyMatch(p -> p.getId().equals(ticket.getProductId()));
        if (!isAuthorized) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "error.ticket.assign.agent.not.authorized");
        }

        Product product = productRepository.findById(ticket.getProductId())
                .orElseThrow(() -> new EntityNotFoundException("Ürün bulunamadı: " + ticket.getProductId()));

        // #3: hedef agent ADMIN/MANAGER (global) ise bilet limiti uygulanmaz.
        Integer effectiveLimit = AuthRoles.isGlobal(targetAgent.getRoles())
                ? null : resolveEffectiveLimit(targetAgentId, product);
        if (effectiveLimit != null) {
            long activeCount = ticketClaimRepository
                    .countActiveTicketsByAgentAndProduct(targetAgentId, product.getId());
            if (activeCount >= effectiveLimit) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "error.ticket.assign.agent.limit.exceeded");
            }
        }

        if (ticketClaimRepository.existsByTicketIdAndAgentId(ticketId, targetAgentId)) {
            log.warn("Hedef agent zaten bu bileti claim almış. Bilet: {}, Agent: {}", ticketId, targetAgentId);
            return ticket;
        }

        TicketClaim claim = TicketClaim.builder()
                .ticket(ticket)
                .agentId(targetAgentId)
                .build();
        ticketClaimRepository.save(claim);

        String previousStatus = ticket.getStatus();
        if ("NEW".equals(previousStatus)) {
            ticket.setStatus(ST_IN_PROGRESS);
            ticketRepository.save(ticket);
            log.info("İlk atama — bilet IN_PROGRESS'e alındı. Bilet: {}", ticketId);
        }

        // Note stays free-form/optional: when the admin supplies none we persist null
        // rather than a hardcoded literal — the audit timeline already renders a
        // localized "Assigned" label per the viewer's language (i18n-correct).
        auditHelper.record(ticket, adminId, "ASSIGN", note,
                previousStatus, ticket.getStatus());

        try {
            workflowService.syncTicketAssignment(ticket, targetAgentId);
        } catch (Exception e) {
            log.error(SYNC_ERR_FMT, ticketId, e.getMessage());
        }

        notificationService.notifyTicketAssigned(ticket, targetAgentId, adminId);

        log.info("Bilet başarıyla atandı. Bilet: {}, Agent: {}", ticketId, targetAgentId);
        return ticket;
    }

    /**
     * Returns whether the given agent is an active claim holder on the ticket.
     *
     * @param ticketId ticket ID
     * @param agentId agent ID
     * @return {@code true} if a claim exists
     */
    public boolean isAgentClaimer(Long ticketId, String agentId) {
        return ticketClaimRepository.existsByTicketIdAndAgentId(ticketId, agentId);
    }

    private Ticket loadTicket(Long id) {
        return ticketRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Bilet bulunamadı: " + id));
    }

    private Integer resolveEffectiveLimit(String agentId, Product product) {
        Integer base = product.getMaxActiveTickets();
        AgentProductLimit custom = agentProductLimitRepository
                .findByAgentIdAndProductId(agentId, product.getId())
                .orElse(null);
        if (custom != null && Boolean.TRUE.equals(custom.getUseCustomLimit())) {
            return custom.getMaxActiveTickets();
        }
        return base;
    }
}
