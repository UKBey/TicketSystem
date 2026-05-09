package com.ticketsystem.it_service_backend.service;

import com.ticketsystem.it_service_backend.entity.AgentProductLimit;
import com.ticketsystem.it_service_backend.entity.Comment;
import com.ticketsystem.it_service_backend.entity.Product;
import com.ticketsystem.it_service_backend.entity.TicketAuditLog;
import com.ticketsystem.it_service_backend.entity.Ticket;
import com.ticketsystem.it_service_backend.entity.TicketClaim;
import com.ticketsystem.it_service_backend.entity.User;
import com.ticketsystem.it_service_backend.event.TicketCreatedEvent;
import com.ticketsystem.it_service_backend.repository.AttachmentRepository;
import com.ticketsystem.it_service_backend.repository.AgentProductLimitRepository;
import com.ticketsystem.it_service_backend.repository.CommentRepository;
import com.ticketsystem.it_service_backend.repository.CsatRepository;
import com.ticketsystem.it_service_backend.repository.ResolutionNoteRepository;
import com.ticketsystem.it_service_backend.repository.TicketAuditLogRepository;
import com.ticketsystem.it_service_backend.repository.TicketClaimRepository;
import com.ticketsystem.it_service_backend.repository.TicketRepository;
import com.ticketsystem.it_service_backend.repository.ProductRepository;
import com.ticketsystem.it_service_backend.repository.UserRepository;
import com.ticketsystem.it_service_backend.repository.WorklogRepository;
import com.ticketsystem.it_service_backend.exception.TicketLimitExceededException;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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
    private final ProductRepository productRepository;
    private final AgentProductLimitRepository agentProductLimitRepository;
    private final CommentRepository commentRepository;
    private final UserRepository userRepository;
    private final WorkflowService workflowService;
    private final ApplicationEventPublisher eventPublisher;
    private final CsatRepository csatRepository;
    private final ResolutionNoteRepository resolutionNoteRepository;
    private final WorklogRepository worklogRepository;
    private final AttachmentRepository attachmentRepository;
    private final TicketAuditLogRepository ticketAuditLogRepository;
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

    // -----------------------------------------------------------------
    // Sayfalama + filtreleme destekli listeleme
    // -----------------------------------------------------------------

    @Transactional(readOnly = true)
    public Page<Ticket> getCustomerTicketsPaged(String customerId, String status, String priority, Pageable pageable) {
        if (isSortByPriority(pageable)) {
            boolean asc = isAscending(pageable);
            Pageable unsorted = toUnsorted(pageable);
            return asc
                ? ticketRepository.findByCustomerIdFilteredOrderByPriorityAsc(customerId, status, priority, unsorted)
                : ticketRepository.findByCustomerIdFilteredOrderByPriorityDesc(customerId, status, priority, unsorted);
        }
        if (isSortBySla(pageable)) {
            boolean asc = isAscending(pageable);
            Pageable unsorted = toUnsorted(pageable);
            return asc
                ? ticketRepository.findByCustomerIdFilteredOrderBySlaUrgencyAsc(customerId, status, priority, unsorted)
                : ticketRepository.findByCustomerIdFilteredOrderBySlaUrgencyDesc(customerId, status, priority, unsorted);
        }
        return ticketRepository.findByCustomerIdFiltered(customerId, status, priority, pageable);
    }

    @Transactional(readOnly = true)
    public Page<Ticket> getPoolTicketsPaged(String userId, List<String> roles, String priority, Pageable pageable) {
        if (roles.contains("AGENT_ADMIN")) {
            if (isSortByPriority(pageable)) {
                boolean asc = isAscending(pageable);
                Pageable unsorted = toUnsorted(pageable);
                return asc
                    ? ticketRepository.findAllPoolTicketsFilteredOrderByPriorityAsc(priority, unsorted)
                    : ticketRepository.findAllPoolTicketsFilteredOrderByPriorityDesc(priority, unsorted);
            }
            if (isSortBySla(pageable)) {
                boolean asc = isAscending(pageable);
                Pageable unsorted = toUnsorted(pageable);
                return asc
                    ? ticketRepository.findAllPoolTicketsFilteredOrderBySlaUrgencyAsc(priority, unsorted)
                    : ticketRepository.findAllPoolTicketsFilteredOrderBySlaUrgencyDesc(priority, unsorted);
            }
            return ticketRepository.findAllPoolTicketsFiltered(priority, pageable);
        }
        if (userId == null) return Page.empty(pageable);

        User agent = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Kullanıcı bulunamadı: " + userId));
        List<Long> productIds = agent.getAuthorizedProducts().stream()
                .map(Product::getId).collect(Collectors.toList());
        if (productIds.isEmpty()) return Page.empty(pageable);

        if (isSortByPriority(pageable)) {
            boolean asc = isAscending(pageable);
            Pageable unsorted = toUnsorted(pageable);
            return asc
                ? ticketRepository.findPoolTicketsFilteredOrderByPriorityAsc(productIds, priority, unsorted)
                : ticketRepository.findPoolTicketsFilteredOrderByPriorityDesc(productIds, priority, unsorted);
        }
        if (isSortBySla(pageable)) {
            boolean asc = isAscending(pageable);
            Pageable unsorted = toUnsorted(pageable);
            return asc
                ? ticketRepository.findPoolTicketsFilteredOrderBySlaUrgencyAsc(productIds, priority, unsorted)
                : ticketRepository.findPoolTicketsFilteredOrderBySlaUrgencyDesc(productIds, priority, unsorted);
        }
        return ticketRepository.findPoolTicketsFiltered(productIds, priority, pageable);
    }

    @Transactional(readOnly = true)
    public Page<Ticket> getAgentClaimedTicketsPaged(String agentId, String status, String priority, Pageable pageable) {
        List<Long> ticketIds = ticketClaimRepository.findTicketIdsByAgentId(agentId);
        if (ticketIds.isEmpty()) return Page.empty(pageable);
        if (isSortByPriority(pageable)) {
            boolean asc = isAscending(pageable);
            Pageable unsorted = toUnsorted(pageable);
            return asc
                ? ticketRepository.findClaimedTicketsFilteredOrderByPriorityAsc(ticketIds, status, priority, unsorted)
                : ticketRepository.findClaimedTicketsFilteredOrderByPriorityDesc(ticketIds, status, priority, unsorted);
        }
        if (isSortBySla(pageable)) {
            boolean asc = isAscending(pageable);
            Pageable unsorted = toUnsorted(pageable);
            return asc
                ? ticketRepository.findClaimedTicketsFilteredOrderBySlaUrgencyAsc(ticketIds, status, priority, unsorted)
                : ticketRepository.findClaimedTicketsFilteredOrderBySlaUrgencyDesc(ticketIds, status, priority, unsorted);
        }
        return ticketRepository.findClaimedTicketsFiltered(ticketIds, status, priority, pageable);
    }

    @Transactional(readOnly = true)
    public Page<Ticket> getTeamTicketsPaged(String userId, List<String> roles, String priority, Pageable pageable) {
        if (roles.contains("AGENT_ADMIN")) {
            if (isSortByPriority(pageable)) {
                boolean asc = isAscending(pageable);
                Pageable unsorted = toUnsorted(pageable);
                return asc
                    ? ticketRepository.findAllTeamTicketsFilteredOrderByPriorityAsc(priority, unsorted)
                    : ticketRepository.findAllTeamTicketsFilteredOrderByPriorityDesc(priority, unsorted);
            }
            if (isSortBySla(pageable)) {
                boolean asc = isAscending(pageable);
                Pageable unsorted = toUnsorted(pageable);
                return asc
                    ? ticketRepository.findAllTeamTicketsFilteredOrderBySlaUrgencyAsc(priority, unsorted)
                    : ticketRepository.findAllTeamTicketsFilteredOrderBySlaUrgencyDesc(priority, unsorted);
            }
            return ticketRepository.findAllTeamTicketsFiltered(priority, pageable);
        }
        if (userId == null) return Page.empty(pageable);

        User agent = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Kullanıcı bulunamadı: " + userId));
        List<Long> productIds = agent.getAuthorizedProducts().stream()
                .map(Product::getId).collect(Collectors.toList());
        if (productIds.isEmpty()) return Page.empty(pageable);

        if (isSortByPriority(pageable)) {
            boolean asc = isAscending(pageable);
            Pageable unsorted = toUnsorted(pageable);
            return asc
                ? ticketRepository.findTeamTicketsFilteredOrderByPriorityAsc(productIds, priority, unsorted)
                : ticketRepository.findTeamTicketsFilteredOrderByPriorityDesc(productIds, priority, unsorted);
        }
        if (isSortBySla(pageable)) {
            boolean asc = isAscending(pageable);
            Pageable unsorted = toUnsorted(pageable);
            return asc
                ? ticketRepository.findTeamTicketsFilteredOrderBySlaUrgencyAsc(productIds, priority, unsorted)
                : ticketRepository.findTeamTicketsFilteredOrderBySlaUrgencyDesc(productIds, priority, unsorted);
        }
        return ticketRepository.findTeamTicketsFiltered(productIds, priority, pageable);
    }

    @Transactional(readOnly = true)
    public Page<Ticket> getTicketsByProductPaged(Long productId, String userId, List<String> roles,
                                                  String status, String priority, Pageable pageable) {
        if (roles.contains("AGENT_ADMIN") || roles.contains("MANAGER") || roles.contains("AGENT")) {
            if (isSortByPriority(pageable)) {
                boolean asc = isAscending(pageable);
                Pageable unsorted = toUnsorted(pageable);
                return asc
                    ? ticketRepository.findByProductIdFilteredOrderByPriorityAsc(productId, status, priority, unsorted)
                    : ticketRepository.findByProductIdFilteredOrderByPriorityDesc(productId, status, priority, unsorted);
            }
            if (isSortBySla(pageable)) {
                boolean asc = isAscending(pageable);
                Pageable unsorted = toUnsorted(pageable);
                return asc
                    ? ticketRepository.findByProductIdFilteredOrderBySlaUrgencyAsc(productId, status, priority, unsorted)
                    : ticketRepository.findByProductIdFilteredOrderBySlaUrgencyDesc(productId, status, priority, unsorted);
            }
            return ticketRepository.findByProductIdFiltered(productId, status, priority, pageable);
        }
        if (roles.contains("CUSTOMER")) {
            if (isSortByPriority(pageable)) {
                boolean asc = isAscending(pageable);
                Pageable unsorted = toUnsorted(pageable);
                return asc
                    ? ticketRepository.findByProductIdAndCustomerIdFilteredOrderByPriorityAsc(productId, userId, status, priority, unsorted)
                    : ticketRepository.findByProductIdAndCustomerIdFilteredOrderByPriorityDesc(productId, userId, status, priority, unsorted);
            }
            if (isSortBySla(pageable)) {
                boolean asc = isAscending(pageable);
                Pageable unsorted = toUnsorted(pageable);
                return asc
                    ? ticketRepository.findByProductIdAndCustomerIdFilteredOrderBySlaUrgencyAsc(productId, userId, status, priority, unsorted)
                    : ticketRepository.findByProductIdAndCustomerIdFilteredOrderBySlaUrgencyDesc(productId, userId, status, priority, unsorted);
            }
            return ticketRepository.findByProductIdAndCustomerIdFiltered(productId, userId, status, priority, pageable);
        }
        return Page.empty(pageable);
    }

    // -----------------------------------------------------------------
    // Sort yardimci metodlari
    // -----------------------------------------------------------------

    private boolean isSortByPriority(Pageable pageable) {
        return pageable.getSort().stream()
                .anyMatch(order -> "priority".equals(order.getProperty()));
    }

    private boolean isSortBySla(Pageable pageable) {
        return pageable.getSort().stream()
                .anyMatch(order -> "slaDeadline".equals(order.getProperty()));
    }

    private boolean isAscending(Pageable pageable) {
        return pageable.getSort().stream()
                .filter(order -> "priority".equals(order.getProperty())
                              || "slaDeadline".equals(order.getProperty()))
                .findFirst()
                .map(Sort.Order::isAscending)
                .orElse(true);
    }

    private Pageable toUnsorted(Pageable pageable) {
        return org.springframework.data.domain.PageRequest.of(
                pageable.getPageNumber(), pageable.getPageSize());
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

        Product product = productRepository.findById(ticket.getProductId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                "Ürün bulunamadı: " + ticket.getProductId()));

        Integer effectiveLimit = product.getMaxActiveTickets();
        AgentProductLimit customLimit = agentProductLimitRepository
            .findByAgentIdAndProductId(agentId, product.getId())
            .orElse(null);
        if (customLimit != null && Boolean.TRUE.equals(customLimit.getUseCustomLimit())) {
            effectiveLimit = customLimit.getMaxActiveTickets();
        }

        if (effectiveLimit != null) {
            long activeCount = ticketClaimRepository.countActiveTicketsByAgentAndProduct(agentId, product.getId());
            if (activeCount >= effectiveLimit) {
            throw new TicketLimitExceededException(String.format(
                "Bu ürün için aktif bilet limitinize ulaştınız. Limit: %d", effectiveLimit));
            }
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
        return unclaimTicket(id, agentId, null);
    }

    /**
     * Ajan kendi claim'ini geri bırakır ve sebebini audit log olarak saklar.
     */
    @Transactional
    public Ticket unclaimTicket(Long id, String agentId, String note) {
        log.info("Unclaim isteği. Bilet: {}, Ajan: {}", id, agentId);
        Ticket ticket = getTicketById(id);
        String previousStatus = ticket.getStatus();

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

        recordTicketAuditLog(ticket, agentId, "UNCLAIM", note, previousStatus, ticket.getStatus());
        return ticket;
    }

    /**
     * Bileti kapatır ve kapatma nedenini audit log olarak saklar.
     */
    @Transactional
    public Ticket closeTicket(Long id, String note, String userId, List<String> roles) {
        log.info("Close isteği. Bilet: {}, Kullanıcı: {}", id, userId);
        Ticket ticket = getTicketById(id);
        String oldStatus = ticket.getStatus();

        validateStateTransition(oldStatus, "CLOSED");
        validateStatusChangePermission(ticket, oldStatus, "CLOSED", userId, roles);

        applyStatusSpecificRules(ticket, oldStatus, "CLOSED", userId);

        ticket.setStatus("CLOSED");
        ticket.setClosedAt(ZonedDateTime.now());

        Ticket saved = ticketRepository.save(ticket);
        handleWorkflowSignals(saved, oldStatus, "CLOSED");
        notificationService.notifyStatusChanged(saved, oldStatus);
        recordTicketAuditLog(saved, userId, "CLOSE", note, oldStatus, saved.getStatus());

        return saved;
    }

    // -----------------------------------------------------------------
    // Durum güncellemesi
    // -----------------------------------------------------------------

    @Transactional
    public Ticket updateTicketStatus(Long id, String newStatus, String userId, List<String> roles) {
        log.info("Statü güncelleme. Bilet: {}, Yeni: {}, Kullanıcı: {}", id, newStatus, userId);
        if ("CLOSED".equals(newStatus)) {
            return closeTicket(id, null, userId, roles);
        }

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

    private void recordTicketAuditLog(Ticket ticket, String actorId, String actionType, String note,
                                      String previousState, String newState) {
        TicketAuditLog auditLog = TicketAuditLog.builder()
                .ticket(ticket)
                .actorId(actorId)
                .actionType(actionType)
                .note(note)
                .previousState(previousState)
                .newState(newState)
                .build();
        ticketAuditLogRepository.save(auditLog);
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
        if (roles.contains("AGENT_ADMIN")) {
            // AGENT_ADMIN statü değişikliği yapabilmek için claim almış olmalıdır.
            // Assign işlemi bu metoddan geçmez, doğrudan assignTicket'tan yapılır.
            boolean hasClaim = ticketClaimRepository.existsByTicketIdAndAgentId(ticket.getId(), userId);
            if (!hasClaim) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "Bu işlemi yapabilmek için önce bileti claim almanız veya join olmanız gerekiyor.");
            }
            return;
        }

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
    // Manuel Atama (Agent Admin)
    // -----------------------------------------------------------------

    /**
     * Agent Admin tarafından belirtilen bileti hedef agent'a manuel olarak atar.
     * Kapasite kontrolü, yetki doğrulaması ve audit log kaydı içerir.
     */
    @Transactional
    public Ticket assignTicket(Long ticketId, String targetAgentId, String adminId, String note) {
        log.info("Manuel atama isteği. Bilet: {}, Hedef Agent: {}, Admin: {}", ticketId, targetAgentId, adminId);

        Ticket ticket = getTicketById(ticketId);

        // 1. Kapalı biletler atanamaz
        if ("CLOSED".equals(ticket.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Kapalı biletler atanamaz.");
        }

        // 2. Hedef agent'ı yükle ve ürün yetki kontrolü
        User targetAgent = userRepository.findById(targetAgentId)
                .orElseThrow(() -> new EntityNotFoundException("Agent bulunamadı: " + targetAgentId));

        boolean isAuthorized = targetAgent.getAuthorizedProducts().stream()
                .anyMatch(p -> p.getId().equals(ticket.getProductId()));
        if (!isAuthorized) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Hedef agent bu ürün için yetkili değil.");
        }

        // 3. Kapasite kontrolü
        Product product = productRepository.findById(ticket.getProductId())
                .orElseThrow(() -> new EntityNotFoundException("Ürün bulunamadı: " + ticket.getProductId()));

        Integer effectiveLimit = product.getMaxActiveTickets();
        AgentProductLimit customLimit = agentProductLimitRepository
                .findByAgentIdAndProductId(targetAgentId, product.getId())
                .orElse(null);
        if (customLimit != null && Boolean.TRUE.equals(customLimit.getUseCustomLimit())) {
            effectiveLimit = customLimit.getMaxActiveTickets();
        }

        if (effectiveLimit != null) {
            long activeCount = ticketClaimRepository
                    .countActiveTicketsByAgentAndProduct(targetAgentId, product.getId());
            if (activeCount >= effectiveLimit) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        String.format("Hedef agent'ın aktif bilet limiti doldu. Limit: %d", effectiveLimit));
            }
        }

        // 4. Zaten claim almışsa tekrar ekleme, sadece log
        if (ticketClaimRepository.existsByTicketIdAndAgentId(ticketId, targetAgentId)) {
            log.warn("Hedef agent zaten bu bileti claim almış. Bilet: {}, Agent: {}", ticketId, targetAgentId);
            return ticket;
        }

        // 5. Claim kaydı oluştur
        TicketClaim claim = TicketClaim.builder()
                .ticket(ticket)
                .agentId(targetAgentId)
                .build();
        ticketClaimRepository.save(claim);

        // 6. İlk claim ise statüyü IN_PROGRESS'e çek
        String previousStatus = ticket.getStatus();
        if ("NEW".equals(previousStatus)) {
            ticket.setStatus("IN_PROGRESS");
            ticketRepository.save(ticket);
            log.info("İlk atama — bilet IN_PROGRESS'e alındı. Bilet: {}", ticketId);
        }

        // 7. Audit log kaydet
        recordTicketAuditLog(ticket, adminId, "ASSIGN",
                note != null ? note : "Manuel atama yapıldı",
                previousStatus, ticket.getStatus());

        // 8. Workflow sync
        try {
            workflowService.syncTicketAssignment(ticket, targetAgentId);
        } catch (Exception e) {
            log.error("Workflow sync hatası. TicketId={}, Hata={}", ticketId, e.getMessage());
        }

        // 9. Bildirim
        notificationService.notifyTicketAssigned(ticket, targetAgentId, adminId);

        log.info("Bilet başarıyla atandı. Bilet: {}, Agent: {}", ticketId, targetAgentId);
        return ticket;
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
