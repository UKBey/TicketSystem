package com.ticketsystem.it_service_backend.service;

import com.ticketsystem.it_service_backend.entity.AgentProductLimit;
import com.ticketsystem.it_service_backend.entity.Product;
import com.ticketsystem.it_service_backend.entity.Ticket;
import com.ticketsystem.it_service_backend.entity.TicketClaim;
import com.ticketsystem.it_service_backend.entity.User;
import com.ticketsystem.it_service_backend.exception.TicketLimitExceededException;
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
 * Claim, unclaim ve manuel atama (assign) iş kuralları. TicketService'in
 * gereksiz büyümesini engellemek için ayrı bir sınıfta tutuluyor — TicketService
 * dış API'yi koruyup bu servise delege eder.
 *
 * <p>Bilet okuma için TicketRepository'i doğrudan kullanır (TicketService'e bağımlılık
 * yaratmadan döngüsel referansı kırar).
 */
@Log4j2
@Service
@RequiredArgsConstructor
public class TicketClaimService {

    private final TicketRepository ticketRepository;
    private final TicketClaimRepository ticketClaimRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final AgentProductLimitRepository agentProductLimitRepository;
    private final WorkflowService workflowService;
    private final NotificationService notificationService;
    private final TicketAuditHelper auditHelper;

    /**
     * Ajan bileti sahiplenir. CLOSED dışındaki her statüdeki bilet claim alınabilir.
     * NEW ise ilk claim — IN_PROGRESS'e geçer; diğer statülerde statü değişmeden
     * mevcut sahiplenilenlere yeni claim eklenir. Ürün yetkisi ve effective
     * (ürün varsayılan + özel override) limit kontrolleri uygulanır.
     *
     * @param id claim alınacak bilet ID
     * @param agentId işlemi yapan ajan
     * @return güncellenmiş bilet
     * @throws ResponseStatusException 400 statü, 403 ürün yetkisi, 409 zaten claim'li
     * @throws TicketLimitExceededException ajan limiti dolduysa
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

        boolean isAuthorized = agent.getAuthorizedProducts().stream()
                .anyMatch(p -> p.getId().equals(ticket.getProductId()));
        if (!isAuthorized) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "error.ticket.claim.product.forbidden");
        }

        Product product = productRepository.findById(ticket.getProductId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "error.product.not.found"));

        Integer effectiveLimit = resolveEffectiveLimit(agentId, product);
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
            ticket.setStatus("IN_PROGRESS");
            ticketRepository.save(ticket);
            log.info("İlk claim — bilet IN_PROGRESS'e alındı. Bilet: {}", id);
            try {
                workflowService.syncTicketAssignment(ticket, agentId);
            } catch (Exception e) {
                log.error("Workflow sync hatası. TicketId={}, Hata={}", id, e.getMessage());
            }
        }

        notificationService.notifyTicketClaimed(ticket, agentId);
        auditHelper.record(ticket, agentId, "CLAIM", null, currentStatus, ticket.getStatus());
        return ticket;
    }

    /**
     * Ajan kendi claim'ini geri bırakır. Son claim ise bilet NEW'e döner.
     *
     * @param id bilet ID
     * @param agentId işlemi yapan ajan
     * @return güncellenmiş bilet
     */
    @Transactional
    public Ticket unclaimTicket(Long id, String agentId) {
        return unclaimTicket(id, agentId, null, null);
    }

    /**
     * Ajan kendi claim'ini geri bırakır; sebep kodu ve opsiyonel notu audit log'a yazılır.
     * Son claim bırakıldığında bilet NEW havuzuna geri döner ve jBPM tarafı senkronize edilir.
     *
     * @param id bilet ID
     * @param agentId işlemi yapan ajan
     * @param reasonCode bırakma sebebi (boş olamaz)
     * @param note serbest not (OTHER sebebinde zorunlu)
     * @return güncellenmiş bilet
     * @throws ResponseStatusException 400 — ajanın aktif claim'i yoksa veya reason hatalıysa
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
        if (remaining == 0 && "IN_PROGRESS".equals(ticket.getStatus())) {
            log.info("Son claim bırakıldı — bilet havuza (NEW) geri dönüyor. Bilet: {}", id);
            ticket.setStatus("NEW");
            ticketRepository.save(ticket);
            try {
                workflowService.syncTicketStatus(ticket);
            } catch (Exception e) {
                log.error("Workflow sync hatası. TicketId={}, Hata={}", id, e.getMessage());
            }
        }

        auditHelper.record(ticket, agentId, "UNCLAIM", reasonCode, note, previousStatus, ticket.getStatus());
        return ticket;
    }

    /**
     * Agent Admin tarafından belirtilen bileti hedef ajana manuel olarak atar.
     *
     * <p>Hem admin'in hem de hedef ajanın ürün yetkisi doğrulanır, effective
     * limit kontrol edilir. NEW bilette ilk atamada statü IN_PROGRESS'e geçer.
     * Hedef ajan zaten claim'li ise idempotent biter (mevcut bilet döner).
     *
     * @param ticketId hedef bilet ID
     * @param targetAgentId atanacak ajan ID
     * @param adminId atamayı yapan AGENT_ADMIN
     * @param note opsiyonel açıklama (audit'e yazılır)
     * @return atama sonrası bilet
     * @throws ResponseStatusException 400 CLOSED/limit, 403 yetki ihlali
     * @throws EntityNotFoundException admin/agent/ürün bulunamazsa
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
        boolean adminAuthorized = adminUser.getAuthorizedProducts().stream()
                .anyMatch(p -> p.getId().equals(ticket.getProductId()));
        if (!adminAuthorized) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "error.ticket.assign.admin.not.authorized");
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

        Integer effectiveLimit = resolveEffectiveLimit(targetAgentId, product);
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
            ticket.setStatus("IN_PROGRESS");
            ticketRepository.save(ticket);
            log.info("İlk atama — bilet IN_PROGRESS'e alındı. Bilet: {}", ticketId);
        }

        auditHelper.record(ticket, adminId, "ASSIGN",
                note != null ? note : "Manuel atama yapıldı",
                previousStatus, ticket.getStatus());

        try {
            workflowService.syncTicketAssignment(ticket, targetAgentId);
        } catch (Exception e) {
            log.error("Workflow sync hatası. TicketId={}, Hata={}", ticketId, e.getMessage());
        }

        notificationService.notifyTicketAssigned(ticket, targetAgentId, adminId);

        log.info("Bilet başarıyla atandı. Bilet: {}, Agent: {}", ticketId, targetAgentId);
        return ticket;
    }

    /**
     * Verilen ajanın belirtilen biletin aktif claim sahibi olup olmadığını döner.
     *
     * @param ticketId bilet ID
     * @param agentId ajan ID
     * @return claim mevcutsa {@code true}
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
