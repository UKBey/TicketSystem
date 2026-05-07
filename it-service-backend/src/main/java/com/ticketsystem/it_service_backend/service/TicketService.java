package com.ticketsystem.it_service_backend.service;

import com.ticketsystem.it_service_backend.entity.Comment;
import com.ticketsystem.it_service_backend.entity.Product;
import com.ticketsystem.it_service_backend.entity.Ticket;
import com.ticketsystem.it_service_backend.entity.TicketClaim;
import com.ticketsystem.it_service_backend.entity.User;
import com.ticketsystem.it_service_backend.event.TicketCreatedEvent;
import com.ticketsystem.it_service_backend.repository.AttachmentRepository;
import com.ticketsystem.it_service_backend.repository.CommentRepository;
import com.ticketsystem.it_service_backend.repository.CsatRepository;
import com.ticketsystem.it_service_backend.repository.ResolutionNoteRepository;
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

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Log4j2
@Service
@RequiredArgsConstructor
public class TicketService {

    private final TicketRepository ticketRepository;
    private final TicketClaimRepository ticketClaimRepository;
    private final CommentRepository commentRepository;
    private final UserRepository userRepository;
    private final WorkflowService workflowService;
    private final ApplicationEventPublisher eventPublisher;
    private final CsatRepository csatRepository;
    private final ResolutionNoteRepository resolutionNoteRepository;
    private final WorklogRepository worklogRepository;
    private final AttachmentRepository attachmentRepository;
    private final NotificationService notificationService;

    // Durum makinesi: her statuden hangi statulere gecilebilecegini tanimlar.
    private static final Map<String, Set<String>> VALID_TRANSITIONS = Map.of(
            "NEW", Set.of("IN_PROGRESS"),
            "IN_PROGRESS", Set.of("NEW", "WAITING_FOR_CUSTOMER", "RESOLVED", "CLOSED"),
            "WAITING_FOR_CUSTOMER", Set.of("IN_PROGRESS"),
            "RESOLVED", Set.of("IN_PROGRESS", "CLOSED"),
            "CLOSED", Set.of()
    );

    private static final Set<String> SLA_PAUSED_STATES = Set.of("WAITING_FOR_CUSTOMER", "RESOLVED");
    private static final Set<String> SLA_ACTIVE_STATES = Set.of("NEW", "IN_PROGRESS");

    // -----------------------------------------------------------------
    // Bilet oluşturma
    // -----------------------------------------------------------------

    @Transactional
    public Ticket createTicket(Ticket ticket, String customerId) {
        log.info("Yeni bilet oluşturma. Müşteri: {}, Ürün: {}", customerId, ticket.getProductId());

        User customer = userRepository.findById(customerId)
                .orElseThrow(() -> new RuntimeException("Kullanıcı bulunamadı: " + customerId));

        Product product = customer.getAuthorizedProducts().stream()
                .filter(p -> p.getId().equals(ticket.getProductId()))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "Bu ürün için destek kaydı oluşturma yetkiniz yok"));

        if (!Boolean.TRUE.equals(product.getIsActive())) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(422), "Bu ürün şu anda aktif değil");
        }

        ticket.setCustomerId(customerId);
        ticket.setStatus("NEW");

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

        return savedTicket;
    }

    // -----------------------------------------------------------------
    // Listeleme
    // -----------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<Ticket> getAllTickets(String userId, List<String> roles) {
        if (roles.contains("AGENT_ADMIN")) {
            return ticketRepository.findAll();
        }
        if (userId == null) return new ArrayList<>();

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Kullanıcı bulunamadı: " + userId));

        List<Long> productIds = user.getAuthorizedProducts().stream()
                .map(Product::getId).collect(Collectors.toList());

        return ticketRepository.findByCustomerIdOrProductIdIn(userId, productIds);
    }

    public List<Ticket> getCustomerTickets(String customerId) {
        return ticketRepository.findByCustomerId(customerId);
    }

    @Transactional(readOnly = true)
    public List<Ticket> getPoolTickets(String userId, List<String> roles) {
        if (roles.contains("AGENT_ADMIN")) {
            return ticketRepository.findByStatus("NEW");
        }
        if (userId == null) return new ArrayList<>();

        User agent = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Kullanıcı bulunamadı: " + userId));

        List<Long> productIds = agent.getAuthorizedProducts().stream()
                .map(Product::getId).collect(Collectors.toList());

        if (productIds.isEmpty()) return new ArrayList<>();

        return ticketRepository.findByStatusAndProductIdIn("NEW", productIds);
    }

    /**
     * Ajanın bizzat claim aldığı biletleri döner.
     */
    @Transactional(readOnly = true)
    public List<Ticket> getAgentClaimedTickets(String agentId) {
        List<Long> ticketIds = ticketClaimRepository.findTicketIdsByAgentId(agentId);
        if (ticketIds.isEmpty()) return new ArrayList<>();
        return ticketRepository.findAllById(ticketIds);
    }

    /**
     * Ajanın yetkili olduğu ürünlerdeki aktif (IN_PROGRESS / WAITING_FOR_CUSTOMER) biletleri döner.
     * Yeni "Team Tickets" panelini besler.
     */
    @Transactional(readOnly = true)
    public List<Ticket> getTeamTickets(String userId, List<String> roles) {
        if (roles.contains("AGENT_ADMIN")) {
            return ticketRepository.findActiveByProductIdIn(
                    ticketRepository.findAll().stream()
                            .map(Ticket::getProductId)
                            .distinct()
                            .collect(Collectors.toList()));
        }
        if (userId == null) return new ArrayList<>();

        User agent = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Kullanıcı bulunamadı: " + userId));

        List<Long> productIds = agent.getAuthorizedProducts().stream()
                .map(Product::getId).collect(Collectors.toList());

        if (productIds.isEmpty()) return new ArrayList<>();

        return ticketRepository.findActiveByProductIdIn(productIds);
    }

    @Transactional(readOnly = true)
    public List<Ticket> getTicketsByProduct(Long productId, String userId, List<String> roles) {
        if (roles.contains("AGENT_ADMIN") || roles.contains("MANAGER") || roles.contains("AGENT")) {
            return ticketRepository.findByProductId(productId);
        }
        if (roles.contains("CUSTOMER")) {
            return ticketRepository.findByCustomerIdAndProductId(userId, productId);
        }
        return new ArrayList<>();
    }

    public Ticket getTicketById(Long id) {
        return ticketRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Bilet bulunamadı: " + id));
    }

    @Transactional(readOnly = true)
    public Ticket getTicketWithAuth(Long id, String userId, List<String> roles) {
        Ticket ticket = getTicketById(id);

        if (roles.contains("AGENT_ADMIN")) return ticket;

        if (userId.equals(ticket.getCustomerId())) return ticket;

        if (roles.contains("AGENT")) {
            User agent = userRepository.findById(userId).orElseThrow();
            boolean authorized = agent.getAuthorizedProducts().stream()
                    .anyMatch(p -> p.getId().equals(ticket.getProductId()));
            if (authorized) return ticket;
        }

        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bu bileti görüntüleme yetkiniz yok.");
    }

    /**
     * Yorum/dosya/worklog gibi mutasyon işlemleri için sıkı yetki denetimi.
     * Çok-agentli yapıda herhangi bir claimer veya AGENT_ADMIN işlem yapabilir.
     */
    @Transactional(readOnly = true)
    public Ticket validateMutationAccess(Long id, String userId, List<String> roles) {
        Ticket ticket = getTicketById(id);

        if (roles.contains("AGENT_ADMIN")) return ticket;

        if (roles.contains("AGENT")) {
            boolean isClaimer = ticketClaimRepository.existsByTicketIdAndAgentId(id, userId);
            if (isClaimer) return ticket;
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Sadece bu bileti claim almış agentlar işlem yapabilir.");
        }

        if (roles.contains("CUSTOMER") && userId.equals(ticket.getCustomerId())) return ticket;

        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bu işlem için yetkiniz bulunmuyor.");
    }

    /**
     * Ajanın belirtilen bileti claim alıp almadığını kontrol eder.
     */
    public boolean isAgentClaimer(Long ticketId, String agentId) {
        return ticketClaimRepository.existsByTicketIdAndAgentId(ticketId, agentId);
    }

    // -----------------------------------------------------------------
    // Claim & Unclaim
    // -----------------------------------------------------------------

    /**
     * Ajan bileti sahiplenir. NEW ise ilk claim — IN_PROGRESS'e geçer.
     * IN_PROGRESS ise mevcut sahiplenilenlerle birlikte claim eklenir.
     */
    @Transactional
    public Ticket claimTicket(Long id, String agentId) {
        log.info("Claim isteği. Bilet: {}, Ajan: {}", id, agentId);
        Ticket ticket = getTicketById(id);

        String currentStatus = ticket.getStatus();
        if (!"NEW".equals(currentStatus) && !"IN_PROGRESS".equals(currentStatus) && !"WAITING_FOR_CUSTOMER".equals(currentStatus)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Yalnızca NEW, IN_PROGRESS veya WAITING_FOR_CUSTOMER statüsündeki biletler üzerinize alınabilir.");
        }

        User agent = userRepository.findById(agentId)
                .orElseThrow(() -> new RuntimeException("Kullanıcı bulunamadı: " + agentId));

        boolean isAuthorized = agent.getAuthorizedProducts().stream()
                .anyMatch(p -> p.getId().equals(ticket.getProductId()));
        if (!isAuthorized) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Bu ürüne ait biletleri üzerinize alma yetkiniz yok.");
        }

        if (ticketClaimRepository.existsByTicketIdAndAgentId(id, agentId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Bu bileti zaten üzerinize almışsınız.");
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
        return ticket;
    }

    /**
     * Ajan kendi claim'ini geri bırakır.
     * Son claim ise bilet NEW'e döner (havuza geri gider).
     */
    @Transactional
    public Ticket unclaimTicket(Long id, String agentId) {
        log.info("Unclaim isteği. Bilet: {}, Ajan: {}", id, agentId);
        Ticket ticket = getTicketById(id);

        if (!ticketClaimRepository.existsByTicketIdAndAgentId(id, agentId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Bu bilete ait aktif bir claim'iniz bulunmuyor.");
        }

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

        return ticket;
    }

    // -----------------------------------------------------------------
    // Durum güncellemesi
    // -----------------------------------------------------------------

    @Transactional
    public Ticket updateTicketStatus(Long id, String newStatus, String userId, List<String> roles) {
        log.info("Statü güncelleme. Bilet: {}, Yeni: {}, Kullanıcı: {}", id, newStatus, userId);
        Ticket ticket = getTicketById(id);
        String oldStatus = ticket.getStatus();

        validateStateTransition(oldStatus, newStatus);
        validateStatusChangePermission(ticket, oldStatus, newStatus, userId, roles);

        if ("RESOLVED".equals(newStatus) && !resolutionNoteRepository.existsByTicketId(id)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Bileti çözüldü olarak işaretlemek için önce bir çözüm notu oluşturmalısınız.");
        }

        applyStatusSpecificRules(ticket, oldStatus, newStatus, userId);

        ticket.setStatus(newStatus);
        if ("RESOLVED".equals(newStatus)) ticket.setResolvedAt(ZonedDateTime.now());
        else if ("CLOSED".equals(newStatus))  ticket.setClosedAt(ZonedDateTime.now());

        Ticket saved = ticketRepository.save(ticket);
        handleWorkflowSignals(saved, oldStatus, newStatus);

        if ("RESOLVED".equals(newStatus)) notificationService.notifyTicketResolved(saved);
        else                               notificationService.notifyStatusChanged(saved, oldStatus);

        return saved;
    }

    private void validateStateTransition(String current, String next) {
        Set<String> allowed = VALID_TRANSITIONS.get(current);
        if (allowed == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bilinmeyen mevcut durum: " + current);
        }
        if (!allowed.contains(next)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    String.format("Geçersiz durum geçişi: %s → %s. İzin verilenler: %s", current, next, allowed));
        }
    }

    private void validateStatusChangePermission(Ticket ticket, String oldStatus, String newStatus,
                                                 String userId, List<String> roles) {
        if (roles.contains("AGENT_ADMIN")) return;

        if (roles.contains("CUSTOMER")) {
            if (!userId.equals(ticket.getCustomerId())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "Sadece kendi biletlerinizin statüsünü değiştirebilirsiniz.");
            }
            boolean allowed =
                    ("WAITING_FOR_CUSTOMER".equals(oldStatus) && "IN_PROGRESS".equals(newStatus)) ||
                    ("RESOLVED".equals(oldStatus) && "IN_PROGRESS".equals(newStatus)) ||
                    ("RESOLVED".equals(oldStatus) && "CLOSED".equals(newStatus));
            if (!allowed) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "Müşteri olarak bu durum geçişini yapamazsınız: " + oldStatus + " → " + newStatus);
            }
            return;
        }

        if (roles.contains("AGENT")) {
            User agent = userRepository.findById(userId).orElseThrow();
            boolean authorized = agent.getAuthorizedProducts().stream()
                    .anyMatch(p -> p.getId().equals(ticket.getProductId()));
            if (!authorized) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bu bileti güncelleme yetkiniz yok.");
            }
            return;
        }

        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bu işlem için yetkiniz bulunmuyor.");
    }

    /**
     * IN_PROGRESS → NEW geçişi: tüm claim'ler temizlenir, bilet havuza geri döner.
     * Bu geçiş yalnızca AGENT_ADMIN yetkisi gerektirdiğinden, normal unclaim için
     * DELETE /api/tickets/{id}/claim kullanılmalıdır.
     */
    private void applyStatusSpecificRules(Ticket ticket, String oldStatus, String newStatus, String userId) {
        if ("IN_PROGRESS".equals(oldStatus) && "NEW".equals(newStatus)) {
            log.warn("AUDIT: Tüm claim'ler temizleniyor. Bilet: {}, İşlemi yapan: {}", ticket.getId(), userId);
            ticketClaimRepository.deleteByTicketId(ticket.getId());
        }

        if ("IN_PROGRESS".equals(oldStatus) && "CLOSED".equals(newStatus)) {
            log.warn("AUDIT: Ajan müşteri yanıtı beklerken bileti kapattı. Bilet: {}, Ajan: {}",
                    ticket.getId(), userId);
        }
    }

    private void handleWorkflowSignals(Ticket ticket, String oldStatus, String newStatus) {
        try {
            workflowService.syncTicketStatus(ticket);

            if (SLA_ACTIVE_STATES.contains(oldStatus) && SLA_PAUSED_STATES.contains(newStatus)) {
                workflowService.pauseSla(ticket);
                ticketRepository.save(ticket);
            }
            if (SLA_PAUSED_STATES.contains(oldStatus) && SLA_ACTIVE_STATES.contains(newStatus)) {
                workflowService.resumeSla(ticket);
                ticketRepository.save(ticket);
            }
            if ("CLOSED".equals(newStatus)) {
                workflowService.closeTicketWorkflow(ticket);
            }
        } catch (Exception e) {
            log.error("Workflow sinyal hatası. TicketId={}, Geçiş={} → {}, Hata={}",
                    ticket.getId(), oldStatus, newStatus, e.getMessage());
        }
    }

    // -----------------------------------------------------------------
    // Silme
    // -----------------------------------------------------------------

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
        resolutionNoteRepository.deleteByTicketId(id);
        worklogRepository.deleteByTicketId(id);
        attachmentRepository.deleteByTicketId(id);
        ticketRepository.deleteById(id);
    }

    // -----------------------------------------------------------------
    // SLA
    // -----------------------------------------------------------------

    public Map<String, Long> getSlaTimerInfo(Long id) {
        Ticket ticket = ticketRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Bilet bulunamadı: " + id));
        return workflowService.getSlaTimerInfo(ticket);
    }

    public Map<String, Long> getSlaTimerInfo(Ticket ticket) {
        return workflowService.getSlaTimerInfo(ticket);
    }
}
