package com.ticketsystem.it_service_backend.service;

import com.ticketsystem.it_service_backend.entity.Product;
import com.ticketsystem.it_service_backend.entity.Ticket;
import com.ticketsystem.it_service_backend.entity.TicketStatus;
import com.ticketsystem.it_service_backend.entity.User;
import com.ticketsystem.it_service_backend.dto.TicketFilterDTO;
import com.ticketsystem.it_service_backend.repository.ProductRepository;
import com.ticketsystem.it_service_backend.repository.TicketClaimRepository;
import com.ticketsystem.it_service_backend.repository.TicketRepository;
import com.ticketsystem.it_service_backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ticketsystem.it_service_backend.util.AuthRoles;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Read-side service for tickets: listing, pagination and advanced filtering.
 *
 * <p>Every query here is read-only. It translates a {@link TicketFilterDTO} plus a
 * {@link Pageable} into the right {@link TicketRepository} call, applying role-based
 * product scoping ({@link AuthRoles}) and the sort/filter routing that used to live in
 * {@code TicketService}. Holds no mutation logic and depends only on repositories, so it
 * never participates in a write transaction.
 */
@Log4j2
@Service
@RequiredArgsConstructor
public class TicketQueryService {
    private static final String ST_RESOLVED = "RESOLVED";
    private static final String ST_WAITING = "WAITING_FOR_CUSTOMER";
    private static final String ST_IN_PROGRESS = "IN_PROGRESS";
    private static final String ST_CLOSED = "CLOSED";
    private static final String MSG_USER_NOT_FOUND = "Kullanıcı bulunamadı: ";
    private static final String VAR_SLA_DEADLINE = "slaDeadline";
    private static final String VAR_STATUS = "status";

    private final TicketRepository ticketRepository;
    private final TicketClaimRepository ticketClaimRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final SlaPolicyService slaPolicyService;

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
            return ticketRepository.findByStatus(TicketStatus.NEW);
        }
        if (userId == null) return new ArrayList<>();

        User agent = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException(MSG_USER_NOT_FOUND + userId));

        List<Long> productIds = agent.getAuthorizedProducts().stream()
                .map(Product::getId).toList();

        if (productIds.isEmpty()) return new ArrayList<>();

        return ticketRepository.findByStatusAndProductIdIn(TicketStatus.NEW, productIds);
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
            List<Ticket> all = ticketRepository.findByCustomerIdFiltered(
                    customerId, f.getStatuses(), f.getPriorities(), Pageable.unpaged()).getContent();
            return slaSortedPage(all, pageable);
        }
        if (isSortByStatus(pageable)) {
            boolean asc = isAscending(pageable);
            Pageable u = toUnsorted(pageable);
            return asc
                ? ticketRepository.findByCustomerIdFilteredOrderByStatusAsc(customerId, f.getStatuses(), f.getPriorities(), u)
                : ticketRepository.findByCustomerIdFilteredOrderByStatusDesc(customerId, f.getStatuses(), f.getPriorities(), u);
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
            List<Ticket> all = ticketRepository.findPoolTicketsFiltered(
                    productIds, f.getPriorities(), Pageable.unpaged()).getContent();
            return slaSortedPage(all, pageable);
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
            List<Ticket> all = ticketRepository.findClaimedTicketsFiltered(
                    ticketIds, f.getStatuses(), f.getPriorities(), Pageable.unpaged()).getContent();
            return slaSortedPage(all, pageable);
        }
        if (isSortByStatus(pageable)) {
            boolean asc = isAscending(pageable);
            Pageable u = toUnsorted(pageable);
            return asc
                ? ticketRepository.findClaimedTicketsFilteredOrderByStatusAsc(ticketIds, f.getStatuses(), f.getPriorities(), u)
                : ticketRepository.findClaimedTicketsFilteredOrderByStatusDesc(ticketIds, f.getStatuses(), f.getPriorities(), u);
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
            List<Ticket> all = ticketRepository.findTeamTicketsFiltered(
                    productIds, statuses, f.getPriorities(), Pageable.unpaged()).getContent();
            return slaSortedPage(all, pageable);
        }
        if (isSortByStatus(pageable)) {
            boolean asc = isAscending(pageable);
            Pageable u = toUnsorted(pageable);
            return asc
                ? ticketRepository.findTeamTicketsFilteredOrderByStatusAsc(productIds, statuses, f.getPriorities(), u)
                : ticketRepository.findTeamTicketsFilteredOrderByStatusDesc(productIds, statuses, f.getPriorities(), u);
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
                // Un-synced kullanıcı → yetkili ürün yok say; 500 yerine boş sayfa döner.
                User agent = userRepository.findById(userId).orElse(null);
                boolean authorized = agent != null && agent.getAuthorizedProducts().stream()
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
                List<Ticket> all = ticketRepository.findByProductIdFiltered(
                        productId, f.getStatuses(), f.getPriorities(), Pageable.unpaged()).getContent();
                return slaSortedPage(all, pageable);
            }
            if (isSortByStatus(pageable)) {
                boolean asc = isAscending(pageable);
                Pageable u = toUnsorted(pageable);
                return asc
                    ? ticketRepository.findByProductIdFilteredOrderByStatusAsc(productId, f.getStatuses(), f.getPriorities(), u)
                    : ticketRepository.findByProductIdFilteredOrderByStatusDesc(productId, f.getStatuses(), f.getPriorities(), u);
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
                List<Ticket> all = ticketRepository.findByProductIdAndCustomerIdFiltered(
                        productId, userId, f.getStatuses(), f.getPriorities(), Pageable.unpaged()).getContent();
                return slaSortedPage(all, pageable);
            }
            if (isSortByStatus(pageable)) {
                boolean asc = isAscending(pageable);
                Pageable u = toUnsorted(pageable);
                return asc
                    ? ticketRepository.findByProductIdAndCustomerIdFilteredOrderByStatusAsc(productId, userId, f.getStatuses(), f.getPriorities(), u)
                    : ticketRepository.findByProductIdAndCustomerIdFilteredOrderByStatusDesc(productId, userId, f.getStatuses(), f.getPriorities(), u);
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

    private boolean isSortByStatus(Pageable pageable) {
        return pageable.getSort().stream()
                .anyMatch(order -> VAR_STATUS.equals(order.getProperty()));
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
                              || VAR_STATUS.equals(order.getProperty())
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
     * Sorts the given tickets by SLA <em>remaining time</em> and returns the requested page.
     *
     * <p>The DB fetch only scopes/filters the rows (and gives a stable base order); the
     * user-visible order is applied here because a PAUSED ticket's displayed remaining is
     * frozen at {@code budget(priority) - elapsed} and bears no relation to {@code slaDeadline}
     * (only re-projected on resume) — ordering by the deadline column would scatter paused
     * tickets. {@link #slaRemainingForSort} mirrors {@code WorkflowService.getSlaTimerInfo} so
     * the order matches the SLA badge exactly. Row counts are bounded by the role/product
     * scope, so this in-memory pass is cheap.
     */
    private Page<Ticket> slaSortedPage(List<Ticket> tickets, Pageable pageable) {
        Comparator<Long> byValue = isAscending(pageable) ? Comparator.naturalOrder() : Comparator.reverseOrder();
        // Completed (CLOSED → null remaining) always sinks to the end, regardless of direction.
        Comparator<Ticket> cmp = Comparator
                .comparing(this::slaRemainingForSort, Comparator.nullsLast(byValue))
                .thenComparing(Ticket::getId);
        List<Ticket> sorted = new ArrayList<>(tickets);
        sorted.sort(cmp);

        int total = sorted.size();
        int from = (int) Math.min(pageable.getOffset(), total);
        int to = Math.min(from + pageable.getPageSize(), total);
        return new PageImpl<>(sorted.subList(from, to), pageable, total);
    }

    /**
     * Remaining SLA time (ms) used only for sorting; mirrors {@code WorkflowService.getSlaTimerInfo}:
     * CLOSED → {@code null} (sinks last, shown as "Completed"); breached/expired → 0 (most urgent end);
     * paused (paused-at set or WAITING/RESOLVED) → {@code budget(priority) - elapsed}; otherwise the
     * live {@code slaDeadline - now}. Floored at 0 so all expired tickets tie at the urgent end.
     */
    private Long slaRemainingForSort(Ticket t) {
        TicketStatus status = t.getStatus();
        if (status == TicketStatus.CLOSED) return null;
        if (Boolean.TRUE.equals(t.getSlaBreached())) return 0L;

        long elapsed = t.getSlaElapsedMs() != null ? t.getSlaElapsedMs() : 0L;
        boolean paused = t.getSlaPausedAt() != null
                || status == TicketStatus.RESOLVED
                || status == TicketStatus.WAITING_FOR_CUSTOMER;
        if (paused) {
            return Math.max(0L, slaPolicyService.getSlaDurationMs(t.getPriority()) - elapsed);
        }

        long deadline;
        if (t.getSlaDeadline() != null) {
            deadline = t.getSlaDeadline().toInstant().toEpochMilli();
        } else {
            long duration = slaPolicyService.getSlaDurationMs(t.getPriority());
            long resumedMs = t.getSlaResumedAt() != null ? t.getSlaResumedAt().toInstant().toEpochMilli()
                    : t.getCreatedAt() != null ? t.getCreatedAt().toInstant().toEpochMilli()
                    : System.currentTimeMillis();
            deadline = resumedMs + (duration - elapsed);
        }
        return Math.max(0L, deadline - System.currentTimeMillis());
    }

    /**
     * Translates JPQL field names in Pageable to SQL column names for native SQL queries.
     * Spring Data JPA passes the sort field name into the native SQL verbatim, so the
     * SQL name (created_at) must be used instead of the JPQL name (createdAt).
     *
     * <p>Whitelist-only by design: because the property is interpolated raw into the
     * native {@code ORDER BY}, any unmapped/crafted field falls back to a safe column
     * instead of being passed through — this is the SQL-injection guard for {@code sortBy}.
     */
    private Pageable toNativePageable(Pageable pageable) {
        if (!pageable.getSort().isSorted()) {
            return pageable;
        }
        List<Sort.Order> nativeOrders = pageable.getSort().stream()
                .map(order -> {
                    String col = switch (order.getProperty()) {
                        case "id"          -> "id";
                        case "title"       -> "title";
                        case "createdAt"   -> "created_at";
                        case "resolvedAt"  -> "resolved_at";
                        case "closedAt"    -> "closed_at";
                        case VAR_SLA_DEADLINE -> "sla_deadline";
                        case "slaBreached" -> "sla_breached";
                        case "productId"   -> "product_id";
                        case "customerId"  -> "customer_id";
                        // Whitelist-only: deger native ORDER BY'a ham interpole edildigi icin
                        // bilinmeyen/crafted sortBy alanlari SQLi'yi onlemek adina guvenli
                        // varsayilana (created_at) duser. Bkz. UserService.sortColumn.
                        default            -> "created_at";
                    };
                    return order.isAscending() ? Sort.Order.asc(col) : Sort.Order.desc(col);
                })
                .toList();
        return org.springframework.data.domain.PageRequest.of(
                pageable.getPageNumber(), pageable.getPageSize(), Sort.by(nativeOrders));
    }
}
