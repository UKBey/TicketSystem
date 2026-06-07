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
import com.ticketsystem.it_service_backend.util.AuthRoles;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Central service for the ticket lifecycle.
 *
 * <p>Handles creation, status/priority/topic updates and listing/pagination
 * flows. Status transitions are validated by a fixed state machine; SLA
 * pause/resume is mirrored to the jBPM side through {@link WorkflowService}.
 * Mutations go through strict authorization ({@link #validateMutationAccess})
 * and reads through an authorization filter ({@link #getTicketWithAuth}).
 * Claim/unclaim and manual assignment are delegated to {@link TicketClaimService}.
 * Notifications and audit log entries are written on every successful operation
 * via {@link NotificationService} and {@link TicketAuditHelper}.
 */
@Log4j2
@Service
@RequiredArgsConstructor
public class TicketService {
    private static final String ST_RESOLVED = "RESOLVED";
    private static final String ST_WAITING = "WAITING_FOR_CUSTOMER";
    private static final String ST_IN_PROGRESS = "IN_PROGRESS";
    private static final String ST_CLOSED = "CLOSED";
    private static final String MSG_USER_NOT_FOUND = "Kullanıcı bulunamadı: ";
    private static final String VAR_SLA_DEADLINE = "slaDeadline";

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

    // Durum makinesi BPMN'de (ticket-lifecycle.bpmn2) yaşar: her statü explicit bir
    // wait node, geçişler `transition_<TARGET>` signal'leri ile tetiklenir ve geçerlilik
    // BPMN şemasıyla encode edilmiştir. Java tarafında geçiş tablosu YOK — geçişin
    // valid mi olduğunu BPMN belirler. {@link #validateStateTransition} BPMN'i signal
    // edip process variable'ı geri okuyarak senkron olarak doğrular; BPMN reddederse
    // (state node signal'i dinlemiyorsa) 400 fırlatılır.
    private static final Set<String> SLA_PAUSED_STATES = Set.of(ST_WAITING, ST_RESOLVED);
    private static final Set<String> SLA_ACTIVE_STATES = Set.of("NEW", ST_IN_PROGRESS);

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
     * created without a topic ("No Topic"); {@code topicId} and {@code topicNameSnapshot}
     * are left null in that case.
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
                    .findByProductIdAndIsActiveTrueOrderByNameAsc(product.getId()).isEmpty();
            if (hasActiveTopics) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "error.ticket.topic.required");
            }
            // topicId ve topicNameSnapshot null kalır.
        } else {
            TicketTopic topic = ticketTopicRepository.findById(ticket.getTopicId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "error.topic.not.found"));
            if (!topic.getProductId().equals(product.getId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "error.ticket.topic.product.mismatch");
            }
            if (!Boolean.TRUE.equals(topic.getIsActive())) {
                throw new ResponseStatusException(HttpStatusCode.valueOf(422), "error.ticket.topic.inactive");
            }
            ticket.setTopicNameSnapshot(topic.getName());
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

    /**
     * Returns all tickets visible to the user. ADMIN sees everything; other
     * roles see only tickets they own as customer or tickets under their authorized
     * products. Returns an empty list when the user is missing.
     *
     * @param userId requesting user (may be null)
     * @param roles role list of the user
     * @return visible tickets
     */
    @Transactional(readOnly = true)
    public List<Ticket> getAllTickets(String userId, List<String> roles) {
        if (AuthRoles.isGlobal(roles)) {
            return ticketRepository.findAll();
        }
        if (userId == null) return new ArrayList<>();

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException(MSG_USER_NOT_FOUND + userId));

        List<Long> productIds = user.getAuthorizedProducts().stream()
                .map(Product::getId).toList();

        return ticketRepository.findByCustomerIdOrProductIdIn(userId, productIds);
    }

    /**
     * Returns every ticket belonging to the given customer (regardless of status).
     *
     * @param customerId customer ID
     * @return the customer's tickets
     */
    public List<Ticket> getCustomerTickets(String customerId) {
        return ticketRepository.findByCustomerId(customerId);
    }

    /**
     * Returns the pool (NEW) tickets restricted to the agent's authorized products.
     * ADMIN sees every NEW ticket.
     *
     * @param userId requesting user
     * @param roles role list of the user
     * @return pool tickets
     */
    @Transactional(readOnly = true)
    public List<Ticket> getPoolTickets(String userId, List<String> roles) {
        if (AuthRoles.isGlobal(roles)) {
            return ticketRepository.findByStatus("NEW");
        }
        if (userId == null) return new ArrayList<>();

        User agent = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException(MSG_USER_NOT_FOUND + userId));

        List<Long> productIds = agent.getAuthorizedProducts().stream()
                .map(Product::getId).toList();

        if (productIds.isEmpty()) return new ArrayList<>();

        return ticketRepository.findByStatusAndProductIdIn("NEW", productIds);
    }

    /**
     * Returns tickets claimed by the agent.
     *
     * @param agentId agent ID
     * @return claimed tickets
     */
    @Transactional(readOnly = true)
    public List<Ticket> getAgentClaimedTickets(String agentId) {
        List<Long> ticketIds = ticketClaimRepository.findTicketIdsByAgentId(agentId);
        if (ticketIds.isEmpty()) return new ArrayList<>();
        return ticketRepository.findAllById(ticketIds);
    }

    /**
     * Returns active tickets (IN_PROGRESS / WAITING_FOR_CUSTOMER) under the agent's
     * authorized products. Feeds the new "Team Tickets" panel.
     *
     * @param userId requesting user
     * @param roles role list of the user
     * @return active team tickets
     */
    @Transactional(readOnly = true)
    public List<Ticket> getTeamTickets(String userId, List<String> roles) {
        if (AuthRoles.isGlobal(roles)) {
            // Önceden findAll().stream().distinct() ile tüm bileti belleğe çekiyordu;
            // tek SQL sorgusuyla aynı sonuç — bellek/CPU tasarrufu + LazyInit yok.
            return ticketRepository.findAllActive();
        }
        if (userId == null) return new ArrayList<>();

        User agent = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException(MSG_USER_NOT_FOUND + userId));

        List<Long> productIds = agent.getAuthorizedProducts().stream()
                .map(Product::getId).toList();

        if (productIds.isEmpty()) return new ArrayList<>();

        return ticketRepository.findActiveByProductIdIn(productIds);
    }

    /**
     * Returns tickets for a specific product applying role-based filtering.
     * Agent/admin see every ticket; customers see only their own; other roles
     * receive an empty list.
     *
     * @param productId target product ID
     * @param userId requesting user
     * @param roles role list of the user
     * @return visible tickets for the product
     */
    @Transactional(readOnly = true)
    public List<Ticket> getTicketsByProduct(Long productId, String userId, List<String> roles) {
        if (AuthRoles.isGlobal(roles) || AuthRoles.isAgentLevel(roles)) {
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

    /**
     * Returns the customer's tickets with a simple (status+priority) filter, paginated.
     * Wrapper around {@link #getCustomerTicketsFiltered}.
     *
     * @param customerId customer ID
     * @param status optional status
     * @param priority optional priority
     * @param pageable pagination + sort
     * @return paginated ticket list
     */
    @Transactional(readOnly = true)
    public Page<Ticket> getCustomerTicketsPaged(String customerId, String status, String priority, Pageable pageable) {
        return getCustomerTicketsFiltered(customerId,
                TicketFilterDTO.builder().statuses(status != null && !status.isBlank() ? java.util.List.of(status) : null).priorities(priority != null && !priority.isBlank() ? java.util.List.of(priority) : null).build(), pageable);
    }

    /**
     * Returns the customer's tickets using the advanced filter set
     * ({@link TicketFilterDTO} — search, dateRange, slaStatus, agentIds, topicIds,
     * productIds, statuses, priorities) and custom sort fields (priority,
     * slaDeadline), paginated.
     *
     * @param customerId customer ID
     * @param f filter criteria
     * @param pageable pagination + sort
     * @return paginated result
     */
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

    /**
     * Returns pool (NEW) tickets with a simple (priority) filter, paginated.
     *
     * @param userId requesting user
     * @param roles role list of the user
     * @param priority optional priority filter
     * @param pageable pagination
     * @return paginated pool tickets
     */
    @Transactional(readOnly = true)
    public Page<Ticket> getPoolTicketsPaged(String userId, List<String> roles, String priority, Pageable pageable) {
        return getPoolTicketsFiltered(userId, roles,
                TicketFilterDTO.builder().priorities(priority != null && !priority.isBlank() ? java.util.List.of(priority) : null).build(), pageable);
    }

    /**
     * Returns pool tickets using the advanced {@link TicketFilterDTO} filter,
     * paginated. Every role — ADMIN included — is scoped to its own
     * authorized products.
     *
     * @param userId requesting user
     * @param roles role list of the user
     * @param f filter criteria
     * @param pageable pagination + sort
     * @return paginated result (empty page when there are no authorized products)
     */
    @Transactional(readOnly = true)
    public Page<Ticket> getPoolTicketsFiltered(String userId, List<String> roles, TicketFilterDTO f, Pageable pageable) {
        if (userId == null) return Page.empty(pageable);

        // ADMIN / MANAGER global görünürlük: tüm ürünlerin havuzu. AGENT / LEAD_AGENT kendi
        // yetkili ürünleriyle sınırlıdır. (getTeam/getAllAccessible ile aynı scope mantığı.)
        List<Long> productIds = resolveScopedProductIds(userId, roles);
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

    /**
     * Returns the agent's claimed tickets with a simple filter, paginated.
     *
     * @param agentId agent ID
     * @param status optional status filter
     * @param priority optional priority filter
     * @param pageable pagination + sort
     * @return paginated result
     */
    @Transactional(readOnly = true)
    public Page<Ticket> getAgentClaimedTicketsPaged(String agentId, String status, String priority, Pageable pageable) {
        return getAgentClaimedTicketsFiltered(agentId,
                TicketFilterDTO.builder().statuses(status != null && !status.isBlank() ? java.util.List.of(status) : null).priorities(priority != null && !priority.isBlank() ? java.util.List.of(priority) : null).build(), pageable);
    }

    /**
     * Returns the agent's claimed tickets using the advanced filter set, paginated.
     *
     * @param agentId agent ID
     * @param f filter criteria
     * @param pageable pagination + sort
     * @return paginated result (empty page when no claims exist)
     */
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
        if (isSortByCsat(pageable)) {
            boolean asc = isAscending(pageable);
            Pageable u = toUnsorted(pageable);
            return asc
                ? ticketRepository.findClaimedTicketsFullFilteredOrderByCsatAsc(
                    ticketIds, statusesOrAll(f), prioritiesOrAll(f), productIdsOrAll(f),
                    toSearchPattern(f.getSearch()), slaStatusesOrAll(f), hasAgentFilter(f), agentIdsOrPlaceholder(f), hasTopicFilter(f), topicIdsOrPlaceholder(f),
                    f.getCreatedAtFrom(), f.getCreatedAtTo(),
                    csatFilterActive(f), csatRatingsOrPlaceholder(f), csatIncludeNone(f), u)
                : ticketRepository.findClaimedTicketsFullFilteredOrderByCsatDesc(
                    ticketIds, statusesOrAll(f), prioritiesOrAll(f), productIdsOrAll(f),
                    toSearchPattern(f.getSearch()), slaStatusesOrAll(f), hasAgentFilter(f), agentIdsOrPlaceholder(f), hasTopicFilter(f), topicIdsOrPlaceholder(f),
                    f.getCreatedAtFrom(), f.getCreatedAtTo(),
                    csatFilterActive(f), csatRatingsOrPlaceholder(f), csatIncludeNone(f), u);
        }
        if (hasExtraFilters(f)) {
            return ticketRepository.findClaimedTicketsFullFiltered(
                    ticketIds, statusesOrAll(f), prioritiesOrAll(f), productIdsOrAll(f),
                    toSearchPattern(f.getSearch()), slaStatusesOrAll(f), hasAgentFilter(f), agentIdsOrPlaceholder(f), hasTopicFilter(f), topicIdsOrPlaceholder(f),
                    f.getCreatedAtFrom(), f.getCreatedAtTo(),
                    csatFilterActive(f), csatRatingsOrPlaceholder(f), csatIncludeNone(f), toNativePageable(pageable));
        }
        return ticketRepository.findClaimedTicketsFiltered(ticketIds, f.getStatuses(), f.getPriorities(), pageable);
    }

    /**
     * Returns active "team" tickets with a simple (priority) filter, paginated.
     *
     * @param userId requesting user
     * @param roles role list of the user
     * @param priority optional priority
     * @param pageable pagination
     * @return paginated result
     */
    @Transactional(readOnly = true)
    public Page<Ticket> getTeamTicketsPaged(String userId, List<String> roles, String priority, Pageable pageable) {
        return getTeamTicketsFiltered(userId, roles,
                TicketFilterDTO.builder().priorities(priority != null && !priority.isBlank() ? java.util.List.of(priority) : null).build(), pageable);
    }

    /**
     * Returns active "team" tickets (excluding NEW and CLOSED) using the advanced
     * filter set, paginated. Every role — ADMIN included — is scoped to its
     * own authorized products.
     *
     * @param userId requesting user
     * @param roles role list of the user
     * @param f filter criteria
     * @param pageable pagination + sort
     * @return paginated result
     */
    @Transactional(readOnly = true)
    public Page<Ticket> getTeamTicketsFiltered(String userId, List<String> roles, TicketFilterDTO f, Pageable pageable) {
        if (userId == null) return Page.empty(pageable);

        // ADMIN / MANAGER global görünürlük: tüm ürünler. AGENT / LEAD_AGENT kendi
        // yetkili ürünleriyle sınırlıdır.
        List<Long> productIds = resolveScopedProductIds(userId, roles);
        if (productIds.isEmpty()) return Page.empty(pageable);

        List<String> statuses = teamStatusesOrActive(f);
        return routeTeamTicketQuery(productIds, statuses, f, pageable);
    }

    /**
     * Returns tickets in EVERY status across all of the user's authorized products.
     * Unlike {@link #getTeamTicketsFiltered}, it includes NEW/CLOSED tickets.
     * Used by the "All Tickets" page.
     *
     * @param userId requesting user
     * @param roles role list of the user
     * @param f filter criteria
     * @param pageable pagination + sort
     * @return paginated result
     */
    @Transactional(readOnly = true)
    public Page<Ticket> getAllAccessibleTicketsFiltered(String userId, List<String> roles, TicketFilterDTO f, Pageable pageable) {
        if (userId == null) return Page.empty(pageable);

        // ADMIN / MANAGER global görünürlük: tüm ürünler. AGENT / LEAD_AGENT kendi
        // yetkili ürünleriyle sınırlıdır.
        List<Long> productIds = resolveScopedProductIds(userId, roles);
        if (productIds.isEmpty()) return Page.empty(pageable);

        List<String> statuses = (f.getStatuses() != null && !f.getStatuses().isEmpty()) ? f.getStatuses() : ALL_STATUSES;
        return routeTeamTicketQuery(productIds, statuses, f, pageable);
    }

    /**
     * Shared sort/filter routing for team-scoped ticket queries
     * ({@link #getTeamTicketsFiltered} and {@link #getAllAccessibleTicketsFiltered}),
     * which differ only in how the {@code statuses} list is resolved.
     */
    private Page<Ticket> routeTeamTicketQuery(List<Long> productIds, List<String> statuses,
                                              TicketFilterDTO f, Pageable pageable) {
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
        if (isSortByCsat(pageable)) {
            boolean asc = isAscending(pageable);
            Pageable u = toUnsorted(pageable);
            return asc
                ? ticketRepository.findTeamTicketsFullFilteredOrderByCsatAsc(
                    productIds, statuses, prioritiesOrAll(f), productIdsOrAll(f), toSearchPattern(f.getSearch()),
                    slaStatusesOrAll(f), hasAgentFilter(f), agentIdsOrPlaceholder(f), hasTopicFilter(f), topicIdsOrPlaceholder(f), f.getCreatedAtFrom(), f.getCreatedAtTo(),
                    csatFilterActive(f), csatRatingsOrPlaceholder(f), csatIncludeNone(f), u)
                : ticketRepository.findTeamTicketsFullFilteredOrderByCsatDesc(
                    productIds, statuses, prioritiesOrAll(f), productIdsOrAll(f), toSearchPattern(f.getSearch()),
                    slaStatusesOrAll(f), hasAgentFilter(f), agentIdsOrPlaceholder(f), hasTopicFilter(f), topicIdsOrPlaceholder(f), f.getCreatedAtFrom(), f.getCreatedAtTo(),
                    csatFilterActive(f), csatRatingsOrPlaceholder(f), csatIncludeNone(f), u);
        }
        if (hasExtraFilters(f)) {
            return ticketRepository.findTeamTicketsFullFiltered(
                    productIds, statuses, prioritiesOrAll(f), productIdsOrAll(f), toSearchPattern(f.getSearch()),
                    slaStatusesOrAll(f), hasAgentFilter(f), agentIdsOrPlaceholder(f), hasTopicFilter(f), topicIdsOrPlaceholder(f), f.getCreatedAtFrom(), f.getCreatedAtTo(),
                    csatFilterActive(f), csatRatingsOrPlaceholder(f), csatIncludeNone(f), toNativePageable(pageable));
        }
        return ticketRepository.findTeamTicketsFiltered(productIds, statuses, f.getPriorities(), pageable);
    }

    /**
     * Returns tickets for the product with a simple filter, paginated.
     *
     * @param productId target product ID
     * @param userId requesting user
     * @param roles role list of the user
     * @param status optional status
     * @param priority optional priority
     * @param pageable pagination
     * @return paginated result
     */
    @Transactional(readOnly = true)
    public Page<Ticket> getTicketsByProductPaged(Long productId, String userId, List<String> roles,
                                                  String status, String priority, Pageable pageable) {
        return getTicketsByProductFiltered(productId, userId, roles,
                TicketFilterDTO.builder().statuses(status != null && !status.isBlank() ? java.util.List.of(status) : null).priorities(priority != null && !priority.isBlank() ? java.util.List.of(priority) : null).build(), pageable);
    }

    /**
     * Returns tickets for the product using the advanced filter set with role-based
     * authorization, paginated. Customers see only their own tickets; agent/admin
     * see every ticket on the product (provided they have product access).
     *
     * @param productId target product ID
     * @param userId requesting user
     * @param roles role list of the user
     * @param f filter criteria
     * @param pageable pagination + sort
     * @return paginated result (empty page when authorization is missing)
     */
    @Transactional(readOnly = true)
    public Page<Ticket> getTicketsByProductFiltered(Long productId, String userId, List<String> roles,
                                                     TicketFilterDTO f, Pageable pageable) {
        if (AuthRoles.isGlobal(roles) || AuthRoles.isAgentLevel(roles)) {
            // ADMIN / MANAGER global: ürün kapsamını atlar. AGENT / LEAD_AGENT
            // yalnızca yetkili oldukları ürünün biletlerini görebilir.
            if (!AuthRoles.isGlobal(roles)) {
                User agent = userRepository.findById(userId).orElseThrow();
                boolean authorized = agent.getAuthorizedProducts().stream()
                        .anyMatch(p -> p.getId().equals(productId));
                if (!authorized) return Page.empty(pageable);
            }

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

    /**
     * Resolves the product-scope for a staff list query. ADMIN / MANAGER are global
     * (every product); AGENT / LEAD_AGENT are restricted to their authorizedProducts.
     *
     * @param userId requesting user (must be non-null)
     * @param roles role list of the user
     * @return the product IDs in scope (empty when a scoped user has no authorized products)
     */
    private List<Long> resolveScopedProductIds(String userId, List<String> roles) {
        if (AuthRoles.isGlobal(roles)) {
            return productRepository.findAll().stream()
                    .map(Product::getId).toList();
        }
        User agent = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException(MSG_USER_NOT_FOUND + userId));
        return agent.getAuthorizedProducts().stream()
                .map(Product::getId).toList();
    }

    /** Whether any "extra" filter (search, dateFrom, dateTo, slaStatus, agentIds, topicIds, productId) is active. */
    private boolean hasExtraFilters(TicketFilterDTO f) {
        return f.getSearch() != null && !f.getSearch().isBlank()
            || f.getCreatedAtFrom() != null
            || f.getCreatedAtTo() != null
            || (f.getSlaStatuses() != null && !f.getSlaStatuses().isEmpty())
            || (f.getAgentIds() != null && !f.getAgentIds().isEmpty())
            || (f.getTopicIds() != null && !f.getTopicIds().isEmpty())
            || (f.getProductIds() != null && !f.getProductIds().isEmpty())
            || csatFilterActive(f)
            || (f.getStatuses() != null && !f.getStatuses().isEmpty() && f.getStatuses().size() > 1)
            || (f.getPriorities() != null && !f.getPriorities().isEmpty() && f.getPriorities().size() > 1);
    }

    /** Returns whether the agent filter is active. */
    private boolean hasAgentFilter(TicketFilterDTO f) {
        return f.getAgentIds() != null && !f.getAgentIds().isEmpty();
    }

    /** For native IN(:agentIds): returns a sentinel list that matches nothing when the filter is off. */
    private List<String> agentIdsOrPlaceholder(TicketFilterDTO f) {
        return hasAgentFilter(f) ? f.getAgentIds() : List.of("__none__");
    }

    /** Returns whether the topic filter is active. */
    private boolean hasTopicFilter(TicketFilterDTO f) {
        return f.getTopicIds() != null && !f.getTopicIds().isEmpty();
    }

    /** For native IN(:topicIds): returns a sentinel list that matches nothing when the filter is off. */
    private List<Long> topicIdsOrPlaceholder(TicketFilterDTO f) {
        return hasTopicFilter(f) ? f.getTopicIds() : List.of(-1L);
    }

    /**
     * Pattern prepared on the Java side for LOWER(t.title) LIKE :searchPattern.
     * Returns null when blank/null (filter is not applied).
     */
    private String toSearchPattern(String search) {
        if (search == null || search.isBlank()) return null;
        return "%" + search.toLowerCase() + "%";
    }

    private static final List<String> ALL_STATUSES   = List.of("NEW", ST_IN_PROGRESS, ST_WAITING, ST_RESOLVED, ST_CLOSED);
    private static final List<String> ALL_PRIORITIES = List.of("CRITICAL", "HIGH", "MEDIUM", "LOW");
    /** Team list scope: all active statuses except NEW (in the pool) and CLOSED (in history). */
    private static final List<String> ACTIVE_TEAM_STATUSES = List.of(ST_IN_PROGRESS, ST_WAITING, ST_RESOLVED);
    private static final List<String> ALL_SLA_STATUSES = List.of("BREACHED", "ACTIVE", "PAUSED");

    /**
     * Used for IN (:statuses) in native SQL queries.
     * Returns all statuses when null/empty (filter is not applied).
     */
    private List<String> statusesOrAll(TicketFilterDTO f) {
        List<String> s = f.getStatuses();
        return (s != null && !s.isEmpty()) ? s : ALL_STATUSES;
    }

    /**
     * Used by team-list queries: returns the user-selected statuses when present,
     * otherwise the active team statuses (NEW/CLOSED excluded). NEW lives in the Pool
     * and CLOSED lives in History.
     */
    private List<String> teamStatusesOrActive(TicketFilterDTO f) {
        List<String> s = f.getStatuses();
        return (s != null && !s.isEmpty()) ? s : ACTIVE_TEAM_STATUSES;
    }

    /**
     * Used for IN (:priorities) in native SQL queries.
     * Returns all priorities when null/empty (filter is not applied).
     */
    private List<String> prioritiesOrAll(TicketFilterDTO f) {
        List<String> p = f.getPriorities();
        return (p != null && !p.isEmpty()) ? p : ALL_PRIORITIES;
    }

    /**
     * Used for IN (:filterProductIds) in native SQL queries.
     * When null/empty, fetches every product ID from the DB and returns it (filter is not applied).
     * Performance note: for null, the goal is to use a single sentinel instead of a large ID list.
     * Here the null path returns a list covering every product.
     */
    private List<Long> productIdsOrAll(TicketFilterDTO f) {
        List<Long> p = f.getProductIds();
        if (p != null && !p.isEmpty()) return p;
        // Filtre yok — tüm ürün ID'lerini getir
        return productRepository.findAll().stream().map(product -> product.getId()).toList();
    }

    /**
     * Used for IN (:slaStatuses) in native SQL queries.
     * Returns all SLA statuses when null/empty (filter is not applied).
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
                .anyMatch(order -> VAR_SLA_DEADLINE.equals(order.getProperty()));
    }

    private boolean isSortByCsat(Pageable pageable) {
        return pageable.getSort().stream()
                .anyMatch(order -> "csatRating".equals(order.getProperty()));
    }

    /** Whether a CSAT rating filter (any of 1-5 or NONE) is active. */
    private boolean csatFilterActive(TicketFilterDTO f) {
        return f.getCsatRatings() != null && !f.getCsatRatings().isEmpty();
    }

    /**
     * Numeric CSAT ratings (1-5) selected in the filter, or a no-match placeholder
     * (-1) when none are numeric — keeps native {@code IN (...)} clauses valid.
     */
    private List<Integer> csatRatingsOrPlaceholder(TicketFilterDTO f) {
        List<String> raw = f.getCsatRatings();
        if (raw == null) return List.of(-1);
        List<Integer> nums = raw.stream()
                .filter(s -> s != null && !s.isBlank() && s.chars().allMatch(Character::isDigit))
                .map(Integer::valueOf)
                .toList();
        return nums.isEmpty() ? List.of(-1) : nums;
    }

    /** Whether the CSAT filter includes the "NONE" bucket (tickets with no survey). */
    private boolean csatIncludeNone(TicketFilterDTO f) {
        return f.getCsatRatings() != null && f.getCsatRatings().contains("NONE");
    }

    private boolean isAscending(Pageable pageable) {
        return pageable.getSort().stream()
                .filter(order -> "priority".equals(order.getProperty())
                              || VAR_SLA_DEADLINE.equals(order.getProperty())
                              || "csatRating".equals(order.getProperty()))
                .findFirst()
                .map(Sort.Order::isAscending)
                .orElse(true);
    }

    private Pageable toUnsorted(Pageable pageable) {
        return org.springframework.data.domain.PageRequest.of(
                pageable.getPageNumber(), pageable.getPageSize());
    }

    /**
     * Translates JPQL field names in Pageable to SQL column names for native SQL queries.
     * Spring Data JPA passes the sort field name into the native SQL verbatim, so the
     * SQL name (created_at) must be used instead of the JPQL name (createdAt).
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
                        case VAR_SLA_DEADLINE -> "sla_deadline";
                        case "slaBreached" -> "sla_breached";
                        case "productId"   -> "product_id";
                        case "customerId"  -> "customer_id";
                        default            -> order.getProperty();
                    };
                    return order.isAscending() ? Sort.Order.asc(col) : Sort.Order.desc(col);
                })
                .toList();
        return org.springframework.data.domain.PageRequest.of(
                pageable.getPageNumber(), pageable.getPageSize(), Sort.by(nativeOrders));
    }

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
        Ticket ticket = getTicketById(id);
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

        Ticket ticket = getTicketById(id);
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

        Ticket ticket = getTicketWithAuth(id, userId, roles);
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
        // Keep the denormalized name snapshot in sync so the response (and every later
        // read) shows the topic name instead of falling back to "#<id>".
        ticket.setTopicNameSnapshot(newTopic.getName());
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
