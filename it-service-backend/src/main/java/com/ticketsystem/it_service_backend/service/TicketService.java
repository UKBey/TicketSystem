package com.ticketsystem.it_service_backend.service;

import com.ticketsystem.it_service_backend.entity.Comment;
import com.ticketsystem.it_service_backend.entity.Product;
import com.ticketsystem.it_service_backend.entity.Ticket;
import com.ticketsystem.it_service_backend.entity.TicketTopic;
import com.ticketsystem.it_service_backend.repository.TicketTopicRepository;
import com.ticketsystem.it_service_backend.entity.User;
import com.ticketsystem.it_service_backend.event.TicketCreatedEvent;
import com.ticketsystem.it_service_backend.dto.TicketFilterDTO;
import com.ticketsystem.it_service_backend.repository.AttachmentRepository;
import com.ticketsystem.it_service_backend.repository.CommentRepository;
import com.ticketsystem.it_service_backend.repository.CsatRepository;
import com.ticketsystem.it_service_backend.repository.TicketClaimRepository;
import com.ticketsystem.it_service_backend.repository.TicketRepository;
import com.ticketsystem.it_service_backend.repository.ProductRepository;
import com.ticketsystem.it_service_backend.repository.UserRepository;
import com.ticketsystem.it_service_backend.repository.WorklogRepository;
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

        if (ticket.getTopicId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "error.ticket.topic.required");
        }
        TicketTopic topic = ticketTopicRepository.findById(ticket.getTopicId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "error.topic.not.found"));
        if (!topic.getProductId().equals(product.getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "error.ticket.topic.product.mismatch");
        }
        if (!Boolean.TRUE.equals(topic.getIsActive())) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(422), "error.ticket.topic.inactive");
        }
        ticket.setTopicNameSnapshot(topic.getName());

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
            // Önceden findAll().stream().distinct() ile tüm bileti belleğe çekiyordu;
            // tek SQL sorgusuyla aynı sonuç — bellek/CPU tasarrufu + LazyInit yok.
            return ticketRepository.findAllActive();
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
                    toSearchPattern(f.getSearch()), slaStatusesOrAll(f), hasAgentFilter(f), agentIdsOrPlaceholder(f), hasTopicFilter(f), topicIdsOrPlaceholder(f),
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
                    slaStatusesOrAll(f), hasAgentFilter(f), agentIdsOrPlaceholder(f), hasTopicFilter(f), topicIdsOrPlaceholder(f), f.getCreatedAtFrom(), f.getCreatedAtTo(), toNativePageable(pageable));
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
                    toSearchPattern(f.getSearch()), slaStatusesOrAll(f), hasAgentFilter(f), agentIdsOrPlaceholder(f), hasTopicFilter(f), topicIdsOrPlaceholder(f),
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

        List<String> statuses = teamStatusesOrActive(f);
        if (isSortByPriority(pageable)) {
            boolean asc = isAscending(pageable);
            Pageable u = toUnsorted(pageable);
            return asc
                ? ticketRepository.findTeamTicketsFilteredOrderByPriorityAsc(productIds, statuses, f.getPriorities(), u)
                : ticketRepository.findTeamTicketsFilteredOrderByPriorityDesc(productIds, statuses, f.getPriorities(), u);
        }
        if (isSortBySla(pageable)) {
            boolean asc = isAscending(pageable);
            Pageable u = toUnsorted(pageable);
            return asc
                ? ticketRepository.findTeamTicketsFilteredOrderBySlaUrgencyAsc(productIds, statuses, f.getPriorities(), u)
                : ticketRepository.findTeamTicketsFilteredOrderBySlaUrgencyDesc(productIds, statuses, f.getPriorities(), u);
        }
        if (hasExtraFilters(f)) {
            return ticketRepository.findTeamTicketsFullFiltered(
                    productIds, statuses, prioritiesOrAll(f), productIdsOrAll(f), toSearchPattern(f.getSearch()),
                    slaStatusesOrAll(f), hasAgentFilter(f), agentIdsOrPlaceholder(f), hasTopicFilter(f), topicIdsOrPlaceholder(f), f.getCreatedAtFrom(), f.getCreatedAtTo(), toNativePageable(pageable));
        }
        return ticketRepository.findTeamTicketsFiltered(productIds, statuses, f.getPriorities(), pageable);
    }

    /**
     * Kullanıcının yetkili olduğu tüm ürünlerdeki TÜM statülerdeki ticket'ları döner.
     * `getTeamTicketsFiltered`'ın aksine NEW/CLOSED'ları da içerir. "All Tickets" sayfası kullanır.
     */
    @Transactional(readOnly = true)
    public Page<Ticket> getAllAccessibleTicketsFiltered(String userId, List<String> roles, TicketFilterDTO f, Pageable pageable) {
        if (userId == null) return Page.empty(pageable);

        User agent = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Kullanıcı bulunamadı: " + userId));
        List<Long> productIds = agent.getAuthorizedProducts().stream()
                .map(Product::getId).collect(Collectors.toList());
        if (productIds.isEmpty()) return Page.empty(pageable);

        List<String> statuses = (f.getStatuses() != null && !f.getStatuses().isEmpty()) ? f.getStatuses() : ALL_STATUSES;
        if (isSortByPriority(pageable)) {
            boolean asc = isAscending(pageable);
            Pageable u = toUnsorted(pageable);
            return asc
                ? ticketRepository.findTeamTicketsFilteredOrderByPriorityAsc(productIds, statuses, f.getPriorities(), u)
                : ticketRepository.findTeamTicketsFilteredOrderByPriorityDesc(productIds, statuses, f.getPriorities(), u);
        }
        if (isSortBySla(pageable)) {
            boolean asc = isAscending(pageable);
            Pageable u = toUnsorted(pageable);
            return asc
                ? ticketRepository.findTeamTicketsFilteredOrderBySlaUrgencyAsc(productIds, statuses, f.getPriorities(), u)
                : ticketRepository.findTeamTicketsFilteredOrderBySlaUrgencyDesc(productIds, statuses, f.getPriorities(), u);
        }
        if (hasExtraFilters(f)) {
            return ticketRepository.findTeamTicketsFullFiltered(
                    productIds, statuses, prioritiesOrAll(f), productIdsOrAll(f), toSearchPattern(f.getSearch()),
                    slaStatusesOrAll(f), hasAgentFilter(f), agentIdsOrPlaceholder(f), hasTopicFilter(f), topicIdsOrPlaceholder(f), f.getCreatedAtFrom(), f.getCreatedAtTo(), toNativePageable(pageable));
        }
        return ticketRepository.findTeamTicketsFiltered(productIds, statuses, f.getPriorities(), pageable);
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
                        slaStatusesOrAll(f), hasAgentFilter(f), agentIdsOrPlaceholder(f), hasTopicFilter(f), topicIdsOrPlaceholder(f), f.getCreatedAtFrom(), f.getCreatedAtTo(), toNativePageable(pageable));
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
                        slaStatusesOrAll(f), hasAgentFilter(f), agentIdsOrPlaceholder(f), hasTopicFilter(f), topicIdsOrPlaceholder(f), f.getCreatedAtFrom(), f.getCreatedAtTo(), toNativePageable(pageable));
            }
            return ticketRepository.findByProductIdAndCustomerIdFiltered(productId, userId, f.getStatuses(), f.getPriorities(), pageable);
        }
        return Page.empty(pageable);
    }

    /** Herhangi bir "ekstra" filtre (search, dateFrom, dateTo, slaStatus, agentIds, topicIds, productId) aktif mi? */
    private boolean hasExtraFilters(TicketFilterDTO f) {
        return f.getSearch() != null && !f.getSearch().isBlank()
            || f.getCreatedAtFrom() != null
            || f.getCreatedAtTo() != null
            || (f.getSlaStatuses() != null && !f.getSlaStatuses().isEmpty())
            || (f.getAgentIds() != null && !f.getAgentIds().isEmpty())
            || (f.getTopicIds() != null && !f.getTopicIds().isEmpty())
            || (f.getProductIds() != null && !f.getProductIds().isEmpty())
            || (f.getStatuses() != null && !f.getStatuses().isEmpty() && f.getStatuses().size() > 1)
            || (f.getPriorities() != null && !f.getPriorities().isEmpty() && f.getPriorities().size() > 1);
    }

    /** Agent filtresinin aktif olup olmadığını döner. */
    private boolean hasAgentFilter(TicketFilterDTO f) {
        return f.getAgentIds() != null && !f.getAgentIds().isEmpty();
    }

    /** Native IN(:agentIds) için: filtre kapalıyken eşleşmeyecek sentinel liste döner. */
    private List<String> agentIdsOrPlaceholder(TicketFilterDTO f) {
        return hasAgentFilter(f) ? f.getAgentIds() : List.of("__none__");
    }

    /** Topic filtresinin aktif olup olmadığını döner. */
    private boolean hasTopicFilter(TicketFilterDTO f) {
        return f.getTopicIds() != null && !f.getTopicIds().isEmpty();
    }

    /** Native IN(:topicIds) için: filtre kapalıyken eşleşmeyecek sentinel liste döner. */
    private List<Long> topicIdsOrPlaceholder(TicketFilterDTO f) {
        return hasTopicFilter(f) ? f.getTopicIds() : List.of(-1L);
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
    /** Team listesi kapsamı: NEW (havuzda) ve CLOSED (history'de) hariç tüm aktif statüler. */
    private static final List<String> ACTIVE_TEAM_STATUSES = List.of("IN_PROGRESS", "WAITING_FOR_CUSTOMER", "RESOLVED");
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
     * Team list query'lerinde kullanılır: kullanıcı statü seçtiyse onu, yoksa NEW/CLOSED hariç
     * aktif team statülerini döner. NEW'ler Pool'da, CLOSED'lar History'de listelenir.
     */
    private List<String> teamStatusesOrActive(TicketFilterDTO f) {
        List<String> s = f.getStatuses();
        return (s != null && !s.isEmpty()) ? s : ACTIVE_TEAM_STATUSES;
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
        return ticketClaimService.isAgentClaimer(ticketId, agentId);
    }

    // -----------------------------------------------------------------
    // Claim & Unclaim — TicketClaimService'e delege edilir
    // -----------------------------------------------------------------

    public Ticket claimTicket(Long id, String agentId) {
        return ticketClaimService.claimTicket(id, agentId);
    }

    public Ticket unclaimTicket(Long id, String agentId) {
        return ticketClaimService.unclaimTicket(id, agentId);
    }

    public Ticket unclaimTicket(Long id, String agentId, String reasonCode, String note) {
        return ticketClaimService.unclaimTicket(id, agentId, reasonCode, note);
    }

    /**
     * Bileti kapatır; sebep kodu ve opsiyonel notu audit log'a yazılır.
     */
    @Transactional
    public Ticket closeTicket(Long id, String reasonCode, String note, String userId, List<String> roles) {
        log.info("Close isteği. Bilet: {}, Kullanıcı: {}, Sebep: {}", id, userId, reasonCode);
        Ticket ticket = getTicketById(id);
        String oldStatus = ticket.getStatus();

        validateStateTransition(oldStatus, "CLOSED");
        validateStatusChangePermission(ticket, oldStatus, "CLOSED", userId, roles);
        validateReasonInput(reasonCode, note);

        applyStatusSpecificRules(ticket, oldStatus, "CLOSED", userId);

        ticket.setStatus("CLOSED");
        ticket.setClosedAt(ZonedDateTime.now());

        Ticket saved = ticketRepository.save(ticket);
        handleWorkflowSignals(saved, oldStatus, "CLOSED");
        notificationService.notifyStatusChanged(saved, oldStatus);
        recordTicketAuditLog(saved, userId, "CLOSE", reasonCode, note, oldStatus, saved.getStatus());

        return saved;
    }

    // -----------------------------------------------------------------
    // Durum güncellemesi
    // -----------------------------------------------------------------

    @Transactional
    public Ticket updateTicketStatus(Long id, String newStatus, String reasonCode, String note,
                                     String userId, List<String> roles) {
        log.info("Statü güncelleme. Bilet: {}, Yeni: {}, Kullanıcı: {}, Sebep: {}", id, newStatus, userId, reasonCode);
        if ("CLOSED".equals(newStatus)) {
            return closeTicket(id, reasonCode, note, userId, roles);
        }

        Ticket ticket = getTicketById(id);
        String oldStatus = ticket.getStatus();

        validateStateTransition(oldStatus, newStatus);
        validateStatusChangePermission(ticket, oldStatus, newStatus, userId, roles);
        if ("RESOLVED".equals(newStatus)) {
            validateReasonInput(reasonCode, note);
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
        recordTicketAuditLog(saved, userId, actionType, reasonCode, note, oldStatus, newStatus);

        return saved;
    }

    @Transactional
    public Ticket updateTicketPriority(Long id, String newPriority, String reasonCode, String note,
                                       String userId, List<String> roles) {
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
        recordTicketAuditLog(saved, userId, "PRIORITY_CHANGE", reasonCode, note, oldPriority, newPriority);
        return saved;
    }

    @Transactional
    public Ticket updateTicketTopic(Long id, Long newTopicId, String reasonCode, String note,
                                    String userId, List<String> roles) {
        log.info("Konu güncelleme. Bilet: {}, Yeni Topic: {}, Kullanıcı: {}", id, newTopicId, userId);
        if (newTopicId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "error.ticket.topic.required");
        }

        Ticket ticket = getTicketWithAuth(id, userId, roles);
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

        String oldTopicName = oldTopicId != null
                ? ticketTopicRepository.findById(oldTopicId).map(TicketTopic::getName).orElse(String.valueOf(oldTopicId))
                : null;

        ticket.setTopicId(newTopicId);
        Ticket saved = ticketRepository.save(ticket);
        recordTicketAuditLog(saved, userId, "TOPIC_CHANGE", reasonCode, note, oldTopicName, newTopic.getName());
        return saved;
    }

    private void recordTicketAuditLog(Ticket ticket, String actorId, String actionType, String note,
                                      String previousState, String newState) {
        auditHelper.record(ticket, actorId, actionType, note, previousState, newState);
    }

    private void recordTicketAuditLog(Ticket ticket, String actorId, String actionType, String reasonCode,
                                      String note, String previousState, String newState) {
        auditHelper.record(ticket, actorId, actionType, reasonCode, note, previousState, newState);
    }

    private void validateReasonInput(String reasonCode, String note) {
        auditHelper.validateReasonInput(reasonCode, note);
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
    // Manuel Atama — TicketClaimService'e delege edilir
    // -----------------------------------------------------------------

    public Ticket assignTicket(Long ticketId, String targetAgentId, String adminId, String note) {
        return ticketClaimService.assignTicket(ticketId, targetAgentId, adminId, note);
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
