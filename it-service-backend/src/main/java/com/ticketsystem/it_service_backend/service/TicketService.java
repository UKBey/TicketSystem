package com.ticketsystem.it_service_backend.service;

import com.ticketsystem.it_service_backend.entity.AgentProductLimit;
import com.ticketsystem.it_service_backend.entity.Comment;
import com.ticketsystem.it_service_backend.entity.Product;
import com.ticketsystem.it_service_backend.entity.TicketAuditLog;
import com.ticketsystem.it_service_backend.entity.Ticket;
import com.ticketsystem.it_service_backend.entity.TicketClaim;
import com.ticketsystem.it_service_backend.entity.User;
import com.ticketsystem.it_service_backend.event.TicketCreatedEvent;
import com.ticketsystem.it_service_backend.dto.TicketFilterDTO;
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
import com.ticketsystem.it_service_backend.websocket.TicketWebSocketEvent;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.messaging.simp.SimpMessagingTemplate;
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
    private final SlaPolicyService slaPolicyService;
    private final ApplicationEventPublisher eventPublisher;
    private final CsatRepository csatRepository;
    private final ResolutionNoteRepository resolutionNoteRepository;
    private final WorklogRepository worklogRepository;
    private final AttachmentRepository attachmentRepository;
    private final TicketAuditLogRepository ticketAuditLogRepository;
    private final NotificationService notificationService;
    private final SimpMessagingTemplate messagingTemplate;

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
                        "error.ticket.create.product.forbidden"));

        if (!Boolean.TRUE.equals(product.getIsActive())) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(422), "error.product.inactive");
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
        recordTicketAuditLog(savedTicket, customerId, "CREATE", null, null, "NEW");

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
        return getCustomerTicketsFiltered(customerId,
                TicketFilterDTO.builder().statuses(status != null && !status.isBlank() ? java.util.List.of(status) : null).priorities(priority != null && !priority.isBlank() ? java.util.List.of(priority) : null).build(), pageable);
    }

    @Transactional(readOnly = true)
    public Page<Ticket> getCustomerTicketsFiltered(String customerId, TicketFilterDTO f, Pageable pageable) {
        if (isSortByPriority(pageable)) {
            boolean asc = isAscending(pageable);
            Pageable u = toUnsorted(pageable);
            return asc
                ? ticketRepository.findByCustomerIdFilteredOrderByPriorityAsc(customerId, f.getStatuses(), f.getPriorities(), u)
                : ticketRepository.findByCustomerIdFilteredOrderByPriorityDesc(customerId, f.getStatuses(), f.getPriorities(), u);
        }
        if (isSortBySla(pageable)) {
            boolean asc = isAscending(pageable);
            Pageable u = toUnsorted(pageable);
            return asc
                ? ticketRepository.findByCustomerIdFilteredOrderBySlaUrgencyAsc(customerId, f.getStatuses(), f.getPriorities(), u)
                : ticketRepository.findByCustomerIdFilteredOrderBySlaUrgencyDesc(customerId, f.getStatuses(), f.getPriorities(), u);
        }
        // Full filter (includes search, dateFrom, dateTo, slaStatus, agentId, productId)
        if (hasExtraFilters(f)) {
            return ticketRepository.findByCustomerIdFullFiltered(
                    customerId, statusesOrAll(f), prioritiesOrAll(f), productIdsOrAll(f),
                    toSearchPattern(f.getSearch()), slaStatusesOrAll(f), f.getAgentId(),
                    f.getCreatedAtFrom(), f.getCreatedAtTo(), toNativePageable(pageable));
        }
        return ticketRepository.findByCustomerIdFiltered(customerId, f.getStatuses(), f.getPriorities(), pageable);
    }

    @Transactional(readOnly = true)
    public Page<Ticket> getPoolTicketsPaged(String userId, List<String> roles, String priority, Pageable pageable) {
        return getPoolTicketsFiltered(userId, roles,
                TicketFilterDTO.builder().priorities(priority != null && !priority.isBlank() ? java.util.List.of(priority) : null).build(), pageable);
    }

    @Transactional(readOnly = true)
    public Page<Ticket> getPoolTicketsFiltered(String userId, List<String> roles, TicketFilterDTO f, Pageable pageable) {
        if (userId == null) return Page.empty(pageable);

        // AGENT_ADMIN da kendi yetkili ürünleriyle sınırlıdır
        User agent = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Kullanıcı bulunamadı: " + userId));
        List<Long> productIds = agent.getAuthorizedProducts().stream()
                .map(Product::getId).collect(Collectors.toList());
        if (productIds.isEmpty()) return Page.empty(pageable);

        if (isSortByPriority(pageable)) {
            boolean asc = isAscending(pageable);
            Pageable u = toUnsorted(pageable);
            return asc
                ? ticketRepository.findPoolTicketsFilteredOrderByPriorityAsc(productIds, f.getPriorities(), u)
                : ticketRepository.findPoolTicketsFilteredOrderByPriorityDesc(productIds, f.getPriorities(), u);
        }
        if (isSortBySla(pageable)) {
            boolean asc = isAscending(pageable);
            Pageable u = toUnsorted(pageable);
            return asc
                ? ticketRepository.findPoolTicketsFilteredOrderBySlaUrgencyAsc(productIds, f.getPriorities(), u)
                : ticketRepository.findPoolTicketsFilteredOrderBySlaUrgencyDesc(productIds, f.getPriorities(), u);
        }
        if (hasExtraFilters(f)) {
            return ticketRepository.findPoolTicketsFullFiltered(
                    productIds, prioritiesOrAll(f), productIdsOrAll(f), toSearchPattern(f.getSearch()),
                    slaStatusesOrAll(f), f.getAgentId(), f.getCreatedAtFrom(), f.getCreatedAtTo(), toNativePageable(pageable));
        }
        return ticketRepository.findPoolTicketsFiltered(productIds, f.getPriorities(), pageable);
    }

    @Transactional(readOnly = true)
    public Page<Ticket> getAgentClaimedTicketsPaged(String agentId, String status, String priority, Pageable pageable) {
        return getAgentClaimedTicketsFiltered(agentId,
                TicketFilterDTO.builder().statuses(status != null && !status.isBlank() ? java.util.List.of(status) : null).priorities(priority != null && !priority.isBlank() ? java.util.List.of(priority) : null).build(), pageable);
    }

    @Transactional(readOnly = true)
    public Page<Ticket> getAgentClaimedTicketsFiltered(String agentId, TicketFilterDTO f, Pageable pageable) {
        List<Long> ticketIds = ticketClaimRepository.findTicketIdsByAgentId(agentId);
        if (ticketIds.isEmpty()) return Page.empty(pageable);
        if (isSortByPriority(pageable)) {
            boolean asc = isAscending(pageable);
            Pageable u = toUnsorted(pageable);
            return asc
                ? ticketRepository.findClaimedTicketsFilteredOrderByPriorityAsc(ticketIds, f.getStatuses(), f.getPriorities(), u)
                : ticketRepository.findClaimedTicketsFilteredOrderByPriorityDesc(ticketIds, f.getStatuses(), f.getPriorities(), u);
        }
        if (isSortBySla(pageable)) {
            boolean asc = isAscending(pageable);
            Pageable u = toUnsorted(pageable);
            return asc
                ? ticketRepository.findClaimedTicketsFilteredOrderBySlaUrgencyAsc(ticketIds, f.getStatuses(), f.getPriorities(), u)
                : ticketRepository.findClaimedTicketsFilteredOrderBySlaUrgencyDesc(ticketIds, f.getStatuses(), f.getPriorities(), u);
        }
        if (hasExtraFilters(f)) {
            return ticketRepository.findClaimedTicketsFullFiltered(
                    ticketIds, statusesOrAll(f), prioritiesOrAll(f), productIdsOrAll(f),
                    toSearchPattern(f.getSearch()), slaStatusesOrAll(f), f.getAgentId(),
                    f.getCreatedAtFrom(), f.getCreatedAtTo(), toNativePageable(pageable));
        }
        return ticketRepository.findClaimedTicketsFiltered(ticketIds, f.getStatuses(), f.getPriorities(), pageable);
    }

    @Transactional(readOnly = true)
    public Page<Ticket> getTeamTicketsPaged(String userId, List<String> roles, String priority, Pageable pageable) {
        return getTeamTicketsFiltered(userId, roles,
                TicketFilterDTO.builder().priorities(priority != null && !priority.isBlank() ? java.util.List.of(priority) : null).build(), pageable);
    }

    @Transactional(readOnly = true)
    public Page<Ticket> getTeamTicketsFiltered(String userId, List<String> roles, TicketFilterDTO f, Pageable pageable) {
        if (userId == null) return Page.empty(pageable);

        // AGENT_ADMIN da kendi yetkili ürünleriyle sınırlıdır
        User agent = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Kullanıcı bulunamadı: " + userId));
        List<Long> productIds = agent.getAuthorizedProducts().stream()
                .map(Product::getId).collect(Collectors.toList());
        if (productIds.isEmpty()) return Page.empty(pageable);

        if (isSortByPriority(pageable)) {
            boolean asc = isAscending(pageable);
            Pageable u = toUnsorted(pageable);
            return asc
                ? ticketRepository.findTeamTicketsFilteredOrderByPriorityAsc(productIds, f.getPriorities(), u)
                : ticketRepository.findTeamTicketsFilteredOrderByPriorityDesc(productIds, f.getPriorities(), u);
        }
        if (isSortBySla(pageable)) {
            boolean asc = isAscending(pageable);
            Pageable u = toUnsorted(pageable);
            return asc
                ? ticketRepository.findTeamTicketsFilteredOrderBySlaUrgencyAsc(productIds, f.getPriorities(), u)
                : ticketRepository.findTeamTicketsFilteredOrderBySlaUrgencyDesc(productIds, f.getPriorities(), u);
        }
        if (hasExtraFilters(f)) {
            return ticketRepository.findTeamTicketsFullFiltered(
                    productIds, prioritiesOrAll(f), productIdsOrAll(f), toSearchPattern(f.getSearch()),
                    slaStatusesOrAll(f), f.getAgentId(), f.getCreatedAtFrom(), f.getCreatedAtTo(), toNativePageable(pageable));
        }
        return ticketRepository.findTeamTicketsFiltered(productIds, f.getPriorities(), pageable);
    }

    @Transactional(readOnly = true)
    public Page<Ticket> getTicketsByProductPaged(Long productId, String userId, List<String> roles,
                                                  String status, String priority, Pageable pageable) {
        return getTicketsByProductFiltered(productId, userId, roles,
                TicketFilterDTO.builder().statuses(status != null && !status.isBlank() ? java.util.List.of(status) : null).priorities(priority != null && !priority.isBlank() ? java.util.List.of(priority) : null).build(), pageable);
    }

    @Transactional(readOnly = true)
    public Page<Ticket> getTicketsByProductFiltered(Long productId, String userId, List<String> roles,
                                                     TicketFilterDTO f, Pageable pageable) {
        if (roles.contains("AGENT_ADMIN") || roles.contains("AGENT")) {
            // Ürün yetkisi kontrolü — hem AGENT hem AGENT_ADMIN için
            User agent = userRepository.findById(userId).orElseThrow();
            boolean authorized = agent.getAuthorizedProducts().stream()
                    .anyMatch(p -> p.getId().equals(productId));
            if (!authorized) return Page.empty(pageable);

            if (isSortByPriority(pageable)) {
                boolean asc = isAscending(pageable);
                Pageable u = toUnsorted(pageable);
                return asc
                    ? ticketRepository.findByProductIdFilteredOrderByPriorityAsc(productId, f.getStatuses(), f.getPriorities(), u)
                    : ticketRepository.findByProductIdFilteredOrderByPriorityDesc(productId, f.getStatuses(), f.getPriorities(), u);
            }
            if (isSortBySla(pageable)) {
                boolean asc = isAscending(pageable);
                Pageable u = toUnsorted(pageable);
                return asc
                    ? ticketRepository.findByProductIdFilteredOrderBySlaUrgencyAsc(productId, f.getStatuses(), f.getPriorities(), u)
                    : ticketRepository.findByProductIdFilteredOrderBySlaUrgencyDesc(productId, f.getStatuses(), f.getPriorities(), u);
            }
            if (hasExtraFilters(f)) {
                return ticketRepository.findByProductIdFullFiltered(
                        productId, statusesOrAll(f), prioritiesOrAll(f), toSearchPattern(f.getSearch()),
                        slaStatusesOrAll(f), f.getAgentId(), f.getCreatedAtFrom(), f.getCreatedAtTo(), toNativePageable(pageable));
            }
            return ticketRepository.findByProductIdFiltered(productId, f.getStatuses(), f.getPriorities(), pageable);
        }
        if (roles.contains("CUSTOMER")) {
            if (isSortByPriority(pageable)) {
                boolean asc = isAscending(pageable);
                Pageable u = toUnsorted(pageable);
                return asc
                    ? ticketRepository.findByProductIdAndCustomerIdFilteredOrderByPriorityAsc(productId, userId, f.getStatuses(), f.getPriorities(), u)
                    : ticketRepository.findByProductIdAndCustomerIdFilteredOrderByPriorityDesc(productId, userId, f.getStatuses(), f.getPriorities(), u);
            }
            if (isSortBySla(pageable)) {
                boolean asc = isAscending(pageable);
                Pageable u = toUnsorted(pageable);
                return asc
                    ? ticketRepository.findByProductIdAndCustomerIdFilteredOrderBySlaUrgencyAsc(productId, userId, f.getStatuses(), f.getPriorities(), u)
                    : ticketRepository.findByProductIdAndCustomerIdFilteredOrderBySlaUrgencyDesc(productId, userId, f.getStatuses(), f.getPriorities(), u);
            }
            if (hasExtraFilters(f)) {
                return ticketRepository.findByProductIdAndCustomerIdFullFiltered(
                        productId, userId, statusesOrAll(f), prioritiesOrAll(f), toSearchPattern(f.getSearch()),
                        slaStatusesOrAll(f), f.getAgentId(), f.getCreatedAtFrom(), f.getCreatedAtTo(), toNativePageable(pageable));
            }
            return ticketRepository.findByProductIdAndCustomerIdFiltered(productId, userId, f.getStatuses(), f.getPriorities(), pageable);
        }
        return Page.empty(pageable);
    }

    /** Herhangi bir "ekstra" filtre (search, dateFrom, dateTo, slaStatus, agentId, productId) aktif mi? */
    private boolean hasExtraFilters(TicketFilterDTO f) {
        return f.getSearch() != null && !f.getSearch().isBlank()
            || f.getCreatedAtFrom() != null
            || f.getCreatedAtTo() != null
            || (f.getSlaStatuses() != null && !f.getSlaStatuses().isEmpty())
            || f.getAgentId() != null && !f.getAgentId().isBlank()
            || (f.getProductIds() != null && !f.getProductIds().isEmpty())
            || (f.getStatuses() != null && !f.getStatuses().isEmpty() && f.getStatuses().size() > 1)
            || (f.getPriorities() != null && !f.getPriorities().isEmpty() && f.getPriorities().size() > 1);
    }

    /**
     * LOWER(t.title) LIKE :searchPattern için Java tarafında hazırlanan pattern.
     * Boş/null ise null döner (filtre uygulanmaz).
     */
    private String toSearchPattern(String search) {
        if (search == null || search.isBlank()) return null;
        return "%" + search.toLowerCase() + "%";
    }

    private static final List<String> ALL_STATUSES   = List.of("NEW", "IN_PROGRESS", "WAITING_FOR_CUSTOMER", "RESOLVED", "CLOSED");
    private static final List<String> ALL_PRIORITIES = List.of("CRITICAL", "HIGH", "MEDIUM", "LOW");
    private static final List<String> ALL_SLA_STATUSES = List.of("BREACHED", "ACTIVE", "PAUSED");

    /**
     * Native SQL query'lerde IN (:statuses) için kullanılır.
     * Null/boş ise tüm statüsleri döner (filtre uygulanmaz).
     */
    private List<String> statusesOrAll(TicketFilterDTO f) {
        List<String> s = f.getStatuses();
        return (s != null && !s.isEmpty()) ? s : ALL_STATUSES;
    }

    /**
     * Native SQL query'lerde IN (:priorities) için kullanılır.
     * Null/boş ise tüm öncelikleri döner (filtre uygulanmaz).
     */
    private List<String> prioritiesOrAll(TicketFilterDTO f) {
        List<String> p = f.getPriorities();
        return (p != null && !p.isEmpty()) ? p : ALL_PRIORITIES;
    }

    /**
     * Native SQL query'lerde IN (:filterProductIds) için kullanılır.
     * Null/boş ise tüm ürün ID'lerini DB'den çekip döner (filtre uygulanmaz).
     * Performans için: null ise çok büyük bir ID listesi yerine özel bir sentinel değer kullanılır.
     * Burada null ise tüm ürünleri kapsayan bir liste döner.
     */
    private List<Long> productIdsOrAll(TicketFilterDTO f) {
        List<Long> p = f.getProductIds();
        if (p != null && !p.isEmpty()) return p;
        // Filtre yok — tüm ürün ID'lerini getir
        return productRepository.findAll().stream().map(product -> product.getId()).collect(Collectors.toList());
    }

    /**
     * Native SQL query'lerde IN (:slaStatuses) için kullanılır.
     * Null/boş ise tüm SLA statüslerini döner (filtre uygulanmaz).
     */
    private List<String> slaStatusesOrAll(TicketFilterDTO f) {
        List<String> s = f.getSlaStatuses();
        return (s != null && !s.isEmpty()) ? s : ALL_SLA_STATUSES;
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

    /**
     * Native SQL sorgular için Pageable'daki JPQL field adlarını SQL column adlarına çevirir.
     * Spring Data JPA native query'de sort field adını olduğu gibi SQL'e ekler,
     * bu yüzden JPQL adı (createdAt) yerine SQL adı (created_at) kullanılmalı.
     */
    private Pageable toNativePageable(Pageable pageable) {
        if (!pageable.getSort().isSorted()) {
            return pageable;
        }
        List<Sort.Order> nativeOrders = pageable.getSort().stream()
                .map(order -> {
                    String col = switch (order.getProperty()) {
                        case "createdAt"   -> "created_at";
                        case "resolvedAt"  -> "resolved_at";
                        case "closedAt"    -> "closed_at";
                        case "slaDeadline" -> "sla_deadline";
                        case "slaBreached" -> "sla_breached";
                        case "productId"   -> "product_id";
                        case "customerId"  -> "customer_id";
                        default            -> order.getProperty();
                    };
                    return order.isAscending() ? Sort.Order.asc(col) : Sort.Order.desc(col);
                })
                .collect(Collectors.toList());
        return org.springframework.data.domain.PageRequest.of(
                pageable.getPageNumber(), pageable.getPageSize(), Sort.by(nativeOrders));
    }

    public Ticket getTicketById(Long id) {
        return ticketRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Bilet bulunamadı: " + id));
    }

    @Transactional(readOnly = true)
    public Ticket getTicketWithAuth(Long id, String userId, List<String> roles) {
        Ticket ticket = getTicketById(id);

        // AGENT_ADMIN: sadece yetkili olduğu ürünlerin biletlerini görebilir
        if (roles.contains("AGENT_ADMIN")) {
            User admin = userRepository.findById(userId).orElseThrow();
            boolean authorized = admin.getAuthorizedProducts().stream()
                    .anyMatch(p -> p.getId().equals(ticket.getProductId()));
            if (authorized) return ticket;
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "error.ticket.view.forbidden");
        }

        if (userId.equals(ticket.getCustomerId())) return ticket;

        if (roles.contains("AGENT")) {
            User agent = userRepository.findById(userId).orElseThrow();
            boolean authorized = agent.getAuthorizedProducts().stream()
                    .anyMatch(p -> p.getId().equals(ticket.getProductId()));
            if (authorized) return ticket;
        }

        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "error.ticket.view.forbidden");
    }

    /**
     * Yorum/dosya/worklog gibi mutasyon işlemleri için sıkı yetki denetimi.
     * AGENT_ADMIN dahil tüm ajanlar için claim kontrolü yapılır.
     */
    @Transactional(readOnly = true)
    public Ticket validateMutationAccess(Long id, String userId, List<String> roles) {
        Ticket ticket = getTicketById(id);

        // AGENT_ADMIN: yetkili ürün + claim kontrolü
        if (roles.contains("AGENT_ADMIN")) {
            User admin = userRepository.findById(userId).orElseThrow();
            boolean productAuthorized = admin.getAuthorizedProducts().stream()
                    .anyMatch(p -> p.getId().equals(ticket.getProductId()));
            if (!productAuthorized) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "error.ticket.view.forbidden");
            }
            boolean isClaimer = ticketClaimRepository.existsByTicketIdAndAgentId(id, userId);
            if (isClaimer) return ticket;
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "error.ticket.only.claimer.can.act");
        }

        if (roles.contains("AGENT")) {
            boolean isClaimer = ticketClaimRepository.existsByTicketIdAndAgentId(id, userId);
            if (isClaimer) return ticket;
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "error.ticket.only.claimer.can.act");
        }

        if (roles.contains("CUSTOMER") && userId.equals(ticket.getCustomerId())) return ticket;

        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "error.forbidden");
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
        recordTicketAuditLog(ticket, agentId, "CLAIM", null, currentStatus, ticket.getStatus());
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
                    "error.ticket.no.active.claim");
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
                    "error.ticket.resolve.requires.note");
        }

        applyStatusSpecificRules(ticket, oldStatus, newStatus, userId);

        ticket.setStatus(newStatus);
        if ("RESOLVED".equals(newStatus)) ticket.setResolvedAt(ZonedDateTime.now());
        else if ("CLOSED".equals(newStatus))  ticket.setClosedAt(ZonedDateTime.now());

        Ticket saved = ticketRepository.save(ticket);
        handleWorkflowSignals(saved, oldStatus, newStatus);

        if ("RESOLVED".equals(newStatus)) notificationService.notifyTicketResolved(saved);
        else                               notificationService.notifyStatusChanged(saved, oldStatus);

        String actionType;
        if ("RESOLVED".equals(newStatus)) actionType = "RESOLVE";
        else if ("IN_PROGRESS".equals(newStatus) && "RESOLVED".equals(oldStatus)) actionType = "REOPEN";
        else if ("WAITING_FOR_CUSTOMER".equals(newStatus)) actionType = "WAITING";
        else if ("IN_PROGRESS".equals(newStatus) && "WAITING_FOR_CUSTOMER".equals(oldStatus)) actionType = "RESUME";
        else actionType = "STATUS_CHANGE";
        recordTicketAuditLog(saved, userId, actionType, null, oldStatus, newStatus);

        return saved;
    }

    @Transactional
    public Ticket updateTicketPriority(Long id, String newPriority, String userId, List<String> roles) {
        log.info("Öncelik güncelleme. Bilet: {}, Yeni Öncelik: {}, Kullanıcı: {}", id, newPriority, userId);
        List<String> valid = List.of("LOW", "MEDIUM", "HIGH", "CRITICAL");
        if (!valid.contains(newPriority)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "error.ticket.invalid.priority");
        }

        Ticket ticket = getTicketWithAuth(id, userId, roles);
        String oldPriority = ticket.getPriority();
        if (oldPriority.equals(newPriority)) return ticket;

        boolean isSlaActive = !Boolean.TRUE.equals(ticket.getSlaBreached())
                && !"CLOSED".equals(ticket.getStatus());
        boolean isPaused    = ticket.getSlaPausedAt() != null
                || "WAITING_FOR_CUSTOMER".equals(ticket.getStatus())
                || "RESOLVED".equals(ticket.getStatus());

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
        recordTicketAuditLog(saved, userId, "PRIORITY_CHANGE", null, oldPriority, newPriority);
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
        broadcastTicketUpdated(ticket.getId());
    }

    // Audit log her ticket mutation'unda kaydedildigi icin broadcast'i da buradan tetikliyoruz.
    private void broadcastTicketUpdated(Long ticketId) {
        try {
            messagingTemplate.convertAndSend("/topic/tickets/" + ticketId, TicketWebSocketEvent.ticketUpdated());
        } catch (Exception e) {
            log.warn("WebSocket broadcast hatasi (ticket {}): {}", ticketId, e.getMessage());
        }
    }

    private void validateStateTransition(String current, String next) {
        Set<String> allowed = VALID_TRANSITIONS.get(current);
        if (allowed == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "error.ticket.unknown.status");
        }
        if (!allowed.contains(next)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "error.ticket.invalid.status.transition");
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
                        "error.ticket.status.requires.claim");
            }
            return;
        }

        if (roles.contains("CUSTOMER")) {
            if (!userId.equals(ticket.getCustomerId())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "error.ticket.status.own.only");
            }
            boolean allowed =
                    ("WAITING_FOR_CUSTOMER".equals(oldStatus) && "IN_PROGRESS".equals(newStatus)) ||
                    ("RESOLVED".equals(oldStatus) && "IN_PROGRESS".equals(newStatus)) ||
                    ("RESOLVED".equals(oldStatus) && "CLOSED".equals(newStatus));
            if (!allowed) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "error.ticket.status.customer.transition");
            }
            return;
        }

        if (roles.contains("AGENT")) {
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
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "error.ticket.assign.closed");
        }

        // 2. Admin'in bu ürün üzerinde yetkisi var mı?
        User adminUser = userRepository.findById(adminId)
                .orElseThrow(() -> new EntityNotFoundException("Admin bulunamadı: " + adminId));
        boolean adminAuthorized = adminUser.getAuthorizedProducts().stream()
                .anyMatch(p -> p.getId().equals(ticket.getProductId()));
        if (!adminAuthorized) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "error.ticket.assign.admin.not.authorized");
        }

        // 3. Hedef agent'ı yükle ve ürün yetki kontrolü
        User targetAgent = userRepository.findById(targetAgentId)
                .orElseThrow(() -> new EntityNotFoundException("Agent bulunamadı: " + targetAgentId));

        boolean isAuthorized = targetAgent.getAuthorizedProducts().stream()
                .anyMatch(p -> p.getId().equals(ticket.getProductId()));
        if (!isAuthorized) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "error.ticket.assign.agent.not.authorized");
        }

        // 4. Kapasite kontrolü
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
                        "error.ticket.assign.agent.limit.exceeded");
            }
        }

        // 5. Zaten claim almışsa tekrar ekleme, sadece log
        if (ticketClaimRepository.existsByTicketIdAndAgentId(ticketId, targetAgentId)) {
            log.warn("Hedef agent zaten bu bileti claim almış. Bilet: {}, Agent: {}", ticketId, targetAgentId);
            return ticket;
        }

        // 6. Claim kaydı oluştur
        TicketClaim claim = TicketClaim.builder()
                .ticket(ticket)
                .agentId(targetAgentId)
                .build();
        ticketClaimRepository.save(claim);

        // 7. İlk claim ise statüyü IN_PROGRESS'e çek
        String previousStatus = ticket.getStatus();
        if ("NEW".equals(previousStatus)) {
            ticket.setStatus("IN_PROGRESS");
            ticketRepository.save(ticket);
            log.info("İlk atama — bilet IN_PROGRESS'e alındı. Bilet: {}", ticketId);
        }

        // 8. Audit log kaydet
        recordTicketAuditLog(ticket, adminId, "ASSIGN",
                note != null ? note : "Manuel atama yapıldı",
                previousStatus, ticket.getStatus());

        // 9. Workflow sync
        try {
            workflowService.syncTicketAssignment(ticket, targetAgentId);
        } catch (Exception e) {
            log.error("Workflow sync hatası. TicketId={}, Hata={}", ticketId, e.getMessage());
        }

        // 10. Bildirim
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

    public Map<String, Object> getSlaTimerInfo(Long id) {
        Ticket ticket = ticketRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Bilet bulunamadı: " + id));
        return workflowService.getSlaTimerInfo(ticket);
    }

    public Map<String, Object> getSlaTimerInfo(Ticket ticket) {
        return workflowService.getSlaTimerInfo(ticket);
    }
}
