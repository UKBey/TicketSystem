package com.ticketsystem.it_service_backend.repository;

import com.ticketsystem.it_service_backend.entity.Ticket;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;
import java.time.ZonedDateTime;
import java.util.List;

/**
 * JPA repository for {@link Ticket} — on top of standard CRUD, exposes a broad API
 * for role-based (customer / agent / admin) filtered paged listings,
 * SLA-urgency sorting, dashboard aggregates, and the SLA breach/warning queries
 * driven by the scheduler.
 *
 * <p>The "full" filtered variants combine search/status/priority/product/agent/topic/
 * SLA/date filters in a single native query; older JPQL variants are kept for
 * backward compatibility.
 */
public interface TicketRepository extends JpaRepository<Ticket, Long> {

    /** Returns all tickets opened by the customer. */
    List<Ticket> findByCustomerId(String customerId);

    /** Returns tickets in the given status (typically "NEW" — the unclaimed pool). */
    List<Ticket> findByStatus(String status);

    /** Tickets in the given status that belong to products the agent is authorized on. */
    List<Ticket> findByStatusAndProductIdIn(String status, List<Long> productIds);

    /** All tickets belonging to the given product list — regardless of status. */
    List<Ticket> findByProductIdIn(List<Long> productIds);

    List<Ticket> findByProductId(Long productId);

    List<Ticket> findByCustomerIdAndProductId(String customerId, Long productId);

    /** Mixed-role: the union of tickets the user opened and tickets on products they're authorized on. */
    List<Ticket> findByCustomerIdOrProductIdIn(String customerId, List<Long> productIds);

    /** Active tickets (i.e. neither NEW nor CLOSED) on products the agent is authorized on. */
    @Query("SELECT t FROM Ticket t WHERE t.productId IN :productIds AND t.status NOT IN ('NEW', 'CLOSED')")
    List<Ticket> findActiveByProductIdIn(@Param("productIds") List<Long> productIds);

    /**
     * All active tickets (for the ADMIN/MANAGER panel). The old code called
     * {@code findAll()} and then deduplicated product_ids in Java — a heavy pattern
     * past 10k+ tickets; this method solves it with a single query.
     */
    @Query("SELECT t FROM Ticket t WHERE t.status NOT IN ('NEW', 'CLOSED')")
    List<Ticket> findAllActive();

    /**
     * All dashboard performance metrics for an agent in a single round-trip.
     * The old {@code getAgentPerformance} triggered three separate findAll() calls
     * (tickets + worklogs + csat) and produced 100MB+ heap spikes; this query
     * aggregates on the DB side.
     *
     * Returns: each row is {@code [agent_id, active_tickets, resolved_24h,
     * sla_breached, avg_resolution_hours, csat_avg]}.
     */
    @Query(value = """
            SELECT
                tc.agent_id                                                          AS agent_id,
                COUNT(CASE WHEN t.status IN ('NEW','IN_PROGRESS','WAITING_FOR_CUSTOMER') THEN 1 END)::BIGINT AS active_tickets,
                COUNT(CASE WHEN t.resolved_at >= :since24h THEN 1 END)::BIGINT        AS resolved_24h,
                COUNT(CASE WHEN t.sla_breached = true THEN 1 END)::BIGINT             AS sla_breached,
                COALESCE(AVG(CASE
                    WHEN t.resolved_at IS NOT NULL AND t.created_at IS NOT NULL
                        THEN EXTRACT(EPOCH FROM (t.resolved_at - t.created_at)) / 3600.0
                END), 0)::DOUBLE PRECISION                                            AS avg_resolution_hours,
                COALESCE(AVG(CAST(cs.rating AS DOUBLE PRECISION)), 0)::DOUBLE PRECISION AS csat_avg
            FROM ticket_claims tc
            JOIN tickets t        ON t.id = tc.ticket_id
            LEFT JOIN csat_surveys cs ON cs.ticket_id = t.id
            WHERE tc.agent_id IN (:agentIds)
            GROUP BY tc.agent_id
            """, nativeQuery = true)
    List<Object[]> findAgentPerformanceMetrics(@Param("agentIds") List<String> agentIds,
                                               @Param("since24h") ZonedDateTime since24h);

    /**
     * Product-scoped variant of {@link #findAgentPerformanceMetrics}. When
     * {@code filterByProduct} is true only claims on tickets belonging to
     * {@code productIds} are aggregated, so a LEAD_AGENT sees their own products only.
     */
    @Query(value = """
            SELECT
                tc.agent_id                                                          AS agent_id,
                COUNT(CASE WHEN t.status IN ('NEW','IN_PROGRESS','WAITING_FOR_CUSTOMER') THEN 1 END)::BIGINT AS active_tickets,
                COUNT(CASE WHEN t.resolved_at >= :since24h THEN 1 END)::BIGINT        AS resolved_24h,
                COUNT(CASE WHEN t.sla_breached = true THEN 1 END)::BIGINT             AS sla_breached,
                COALESCE(AVG(CASE
                    WHEN t.resolved_at IS NOT NULL AND t.created_at IS NOT NULL
                        THEN EXTRACT(EPOCH FROM (t.resolved_at - t.created_at)) / 3600.0
                END), 0)::DOUBLE PRECISION                                            AS avg_resolution_hours,
                COALESCE(AVG(CAST(cs.rating AS DOUBLE PRECISION)), 0)::DOUBLE PRECISION AS csat_avg
            FROM ticket_claims tc
            JOIN tickets t        ON t.id = tc.ticket_id
            LEFT JOIN csat_surveys cs ON cs.ticket_id = t.id
            WHERE tc.agent_id IN (:agentIds)
              AND (:filterByProduct = false OR t.product_id IN (:productIds))
            GROUP BY tc.agent_id
            """, nativeQuery = true)
    List<Object[]> findAgentPerformanceMetricsScoped(@Param("agentIds") List<String> agentIds,
                                                     @Param("since24h") ZonedDateTime since24h,
                                                     @Param("filterByProduct") boolean filterByProduct,
                                                     @Param("productIds") List<Long> productIds);

    // =========================================================================
    // Genel filtreli sorgular — tüm yeni filtre parametrelerini destekler
    // (searchPattern, status, priority, productId, agentId, slaStatus, dateFrom, dateTo)
    // NOT: searchPattern Java tarafında '%' + search.toLowerCase() + '%' olarak hazırlanır.
    // =========================================================================

    /**
     * Customer tickets — all filters supported.
     * slaStatus: BREACHED | ACTIVE | PAUSED | null
     */
    @Query(value = """
        SELECT * FROM tickets t
        WHERE t.customer_id = CAST(:customerId AS text)
          AND (t.status IN (:statuses))
          AND (t.priority IN (:priorities))
          AND (t.product_id IN (:filterProductIds))
          AND (CAST(:searchPattern AS text) IS NULL OR LOWER(t.title) LIKE CAST(:searchPattern AS text))
          AND (CAST(:dateFrom AS timestamptz) IS NULL OR t.created_at >= CAST(:dateFrom AS timestamptz))
          AND (CAST(:dateTo AS timestamptz) IS NULL OR t.created_at <= CAST(:dateTo AS timestamptz))
          AND (('BREACHED' IN (:slaStatuses) AND t.sla_breached = true OR 'ACTIVE' IN (:slaStatuses) AND t.sla_breached = false AND t.sla_paused_at IS NULL OR 'PAUSED' IN (:slaStatuses) AND t.sla_breached = false AND t.sla_paused_at IS NOT NULL))
          AND (:agentFilterActive = FALSE
               OR EXISTS (SELECT 1 FROM ticket_claims tc WHERE tc.ticket_id = t.id AND tc.agent_id IN (:agentIds)))
          AND (:topicFilterActive = FALSE OR t.topic_id IN (:topicIds))
        """, nativeQuery = true, countQuery = """
        SELECT COUNT(*) FROM tickets t
        WHERE t.customer_id = CAST(:customerId AS text)
          AND (t.status IN (:statuses))
          AND (t.priority IN (:priorities))
          AND (t.product_id IN (:filterProductIds))
          AND (CAST(:searchPattern AS text) IS NULL OR LOWER(t.title) LIKE CAST(:searchPattern AS text))
          AND (CAST(:dateFrom AS timestamptz) IS NULL OR t.created_at >= CAST(:dateFrom AS timestamptz))
          AND (CAST(:dateTo AS timestamptz) IS NULL OR t.created_at <= CAST(:dateTo AS timestamptz))
          AND (('BREACHED' IN (:slaStatuses) AND t.sla_breached = true OR 'ACTIVE' IN (:slaStatuses) AND t.sla_breached = false AND t.sla_paused_at IS NULL OR 'PAUSED' IN (:slaStatuses) AND t.sla_breached = false AND t.sla_paused_at IS NOT NULL))
          AND (:agentFilterActive = FALSE
               OR EXISTS (SELECT 1 FROM ticket_claims tc WHERE tc.ticket_id = t.id AND tc.agent_id IN (:agentIds)))
          AND (:topicFilterActive = FALSE OR t.topic_id IN (:topicIds))
        """)
    Page<Ticket> findByCustomerIdFullFiltered(
            @Param("customerId")    String customerId,
            @Param("statuses") List<String> statuses,
            @Param("priorities") List<String> priorities,
            @Param("filterProductIds") List<Long> filterProductIds,
            @Param("searchPattern") String searchPattern,
            @Param("slaStatuses") List<String> slaStatuses,
            @Param("agentFilterActive") Boolean agentFilterActive,
            @Param("agentIds")      List<String> agentIds,
            @Param("topicFilterActive") Boolean topicFilterActive,
            @Param("topicIds")      List<Long> topicIds,
            @Param("dateFrom")      ZonedDateTime dateFrom,
            @Param("dateTo")        ZonedDateTime dateTo,
            Pageable pageable);

    /** Pool (NEW) tickets — authorized products + all filters. */
    @Query(value = """
        SELECT * FROM tickets t
        WHERE t.status = 'NEW'
          AND t.product_id IN :productIds
          AND (t.priority IN (:priorities))
          AND (t.product_id IN (:filterProductIds))
          AND (CAST(:searchPattern AS text) IS NULL OR LOWER(t.title) LIKE CAST(:searchPattern AS text))
          AND (CAST(:dateFrom AS timestamptz) IS NULL OR t.created_at >= CAST(:dateFrom AS timestamptz))
          AND (CAST(:dateTo AS timestamptz) IS NULL OR t.created_at <= CAST(:dateTo AS timestamptz))
          AND (('BREACHED' IN (:slaStatuses) AND t.sla_breached = true OR 'ACTIVE' IN (:slaStatuses) AND t.sla_breached = false AND t.sla_paused_at IS NULL OR 'PAUSED' IN (:slaStatuses) AND t.sla_breached = false AND t.sla_paused_at IS NOT NULL))
          AND (:agentFilterActive = FALSE
               OR EXISTS (SELECT 1 FROM ticket_claims tc WHERE tc.ticket_id = t.id AND tc.agent_id IN (:agentIds)))
          AND (:topicFilterActive = FALSE OR t.topic_id IN (:topicIds))
        """, nativeQuery = true)
    Page<Ticket> findPoolTicketsFullFiltered(
            @Param("productIds")    List<Long> productIds,
            @Param("priorities") List<String> priorities,
            @Param("filterProductIds") List<Long> filterProductIds,
            @Param("searchPattern") String searchPattern,
            @Param("slaStatuses") List<String> slaStatuses,
            @Param("agentFilterActive") Boolean agentFilterActive,
            @Param("agentIds")      List<String> agentIds,
            @Param("topicFilterActive") Boolean topicFilterActive,
            @Param("topicIds")      List<Long> topicIds,
            @Param("dateFrom")      ZonedDateTime dateFrom,
            @Param("dateTo")        ZonedDateTime dateTo,
            Pageable pageable);

    /** Pool (NEW) tickets — ADMIN/MANAGER, all products + all filters. */
    @Query(value = """
        SELECT * FROM tickets t
        WHERE t.status = 'NEW'
          AND (t.priority IN (:priorities))
          AND (t.product_id IN (:filterProductIds))
          AND (CAST(:searchPattern AS text) IS NULL OR LOWER(t.title) LIKE CAST(:searchPattern AS text))
          AND (CAST(:dateFrom AS timestamptz) IS NULL OR t.created_at >= CAST(:dateFrom AS timestamptz))
          AND (CAST(:dateTo AS timestamptz) IS NULL OR t.created_at <= CAST(:dateTo AS timestamptz))
          AND (('BREACHED' IN (:slaStatuses) AND t.sla_breached = true OR 'ACTIVE' IN (:slaStatuses) AND t.sla_breached = false AND t.sla_paused_at IS NULL OR 'PAUSED' IN (:slaStatuses) AND t.sla_breached = false AND t.sla_paused_at IS NOT NULL))
          AND (:agentFilterActive = FALSE
               OR EXISTS (SELECT 1 FROM ticket_claims tc WHERE tc.ticket_id = t.id AND tc.agent_id IN (:agentIds)))
          AND (:topicFilterActive = FALSE OR t.topic_id IN (:topicIds))
        """, nativeQuery = true)
    Page<Ticket> findAllPoolTicketsFullFiltered(
            @Param("priorities") List<String> priorities,
            @Param("filterProductIds") List<Long> filterProductIds,
            @Param("searchPattern") String searchPattern,
            @Param("slaStatuses") List<String> slaStatuses,
            @Param("agentFilterActive") Boolean agentFilterActive,
            @Param("agentIds")      List<String> agentIds,
            @Param("topicFilterActive") Boolean topicFilterActive,
            @Param("topicIds")      List<Long> topicIds,
            @Param("dateFrom")      ZonedDateTime dateFrom,
            @Param("dateTo")        ZonedDateTime dateTo,
            Pageable pageable);

    /** Tickets claimed by the agent — all filters. */
    @Query(value = """
        SELECT * FROM tickets t
        WHERE t.id IN :ticketIds
          AND (t.status IN (:statuses))
          AND (t.priority IN (:priorities))
          AND (t.product_id IN (:filterProductIds))
          AND (CAST(:searchPattern AS text) IS NULL OR LOWER(t.title) LIKE CAST(:searchPattern AS text))
          AND (CAST(:dateFrom AS timestamptz) IS NULL OR t.created_at >= CAST(:dateFrom AS timestamptz))
          AND (CAST(:dateTo AS timestamptz) IS NULL OR t.created_at <= CAST(:dateTo AS timestamptz))
          AND (('BREACHED' IN (:slaStatuses) AND t.sla_breached = true OR 'ACTIVE' IN (:slaStatuses) AND t.sla_breached = false AND t.sla_paused_at IS NULL OR 'PAUSED' IN (:slaStatuses) AND t.sla_breached = false AND t.sla_paused_at IS NOT NULL))
          AND (:agentFilterActive = FALSE
               OR EXISTS (SELECT 1 FROM ticket_claims tc WHERE tc.ticket_id = t.id AND tc.agent_id IN (:agentIds)))
          AND (:topicFilterActive = FALSE OR t.topic_id IN (:topicIds))
        """, nativeQuery = true)
    Page<Ticket> findClaimedTicketsFullFiltered(
            @Param("ticketIds")     List<Long> ticketIds,
            @Param("statuses") List<String> statuses,
            @Param("priorities") List<String> priorities,
            @Param("filterProductIds") List<Long> filterProductIds,
            @Param("searchPattern") String searchPattern,
            @Param("slaStatuses") List<String> slaStatuses,
            @Param("agentFilterActive") Boolean agentFilterActive,
            @Param("agentIds")      List<String> agentIds,
            @Param("topicFilterActive") Boolean topicFilterActive,
            @Param("topicIds")      List<Long> topicIds,
            @Param("dateFrom")      ZonedDateTime dateFrom,
            @Param("dateTo")        ZonedDateTime dateTo,
            Pageable pageable);

    /** Team tickets — authorized products + all filters. */
    @Query(value = """
        SELECT * FROM tickets t
        WHERE t.product_id IN :productIds
          AND (t.status IN (:statuses))
          AND (t.priority IN (:priorities))
          AND (t.product_id IN (:filterProductIds))
          AND (CAST(:searchPattern AS text) IS NULL OR LOWER(t.title) LIKE CAST(:searchPattern AS text))
          AND (CAST(:dateFrom AS timestamptz) IS NULL OR t.created_at >= CAST(:dateFrom AS timestamptz))
          AND (CAST(:dateTo AS timestamptz) IS NULL OR t.created_at <= CAST(:dateTo AS timestamptz))
          AND (('BREACHED' IN (:slaStatuses) AND t.sla_breached = true OR 'ACTIVE' IN (:slaStatuses) AND t.sla_breached = false AND t.sla_paused_at IS NULL OR 'PAUSED' IN (:slaStatuses) AND t.sla_breached = false AND t.sla_paused_at IS NOT NULL))
          AND (:agentFilterActive = FALSE
               OR EXISTS (SELECT 1 FROM ticket_claims tc WHERE tc.ticket_id = t.id AND tc.agent_id IN (:agentIds)))
          AND (:topicFilterActive = FALSE OR t.topic_id IN (:topicIds))
        """, nativeQuery = true)
    Page<Ticket> findTeamTicketsFullFiltered(
            @Param("productIds")    List<Long> productIds,
            @Param("statuses") List<String> statuses,
            @Param("priorities") List<String> priorities,
            @Param("filterProductIds") List<Long> filterProductIds,
            @Param("searchPattern") String searchPattern,
            @Param("slaStatuses") List<String> slaStatuses,
            @Param("agentFilterActive") Boolean agentFilterActive,
            @Param("agentIds")      List<String> agentIds,
            @Param("topicFilterActive") Boolean topicFilterActive,
            @Param("topicIds")      List<Long> topicIds,
            @Param("dateFrom")      ZonedDateTime dateFrom,
            @Param("dateTo")        ZonedDateTime dateTo,
            Pageable pageable);

    /** Team tickets — ADMIN/MANAGER, all products + all filters. */
    @Query(value = """
        SELECT * FROM tickets t
        WHERE t.status NOT IN ('NEW', 'CLOSED')
          AND (t.priority IN (:priorities))
          AND (t.product_id IN (:filterProductIds))
          AND (CAST(:searchPattern AS text) IS NULL OR LOWER(t.title) LIKE CAST(:searchPattern AS text))
          AND (CAST(:dateFrom AS timestamptz) IS NULL OR t.created_at >= CAST(:dateFrom AS timestamptz))
          AND (CAST(:dateTo AS timestamptz) IS NULL OR t.created_at <= CAST(:dateTo AS timestamptz))
          AND (('BREACHED' IN (:slaStatuses) AND t.sla_breached = true OR 'ACTIVE' IN (:slaStatuses) AND t.sla_breached = false AND t.sla_paused_at IS NULL OR 'PAUSED' IN (:slaStatuses) AND t.sla_breached = false AND t.sla_paused_at IS NOT NULL))
          AND (:agentFilterActive = FALSE
               OR EXISTS (SELECT 1 FROM ticket_claims tc WHERE tc.ticket_id = t.id AND tc.agent_id IN (:agentIds)))
          AND (:topicFilterActive = FALSE OR t.topic_id IN (:topicIds))
        """, nativeQuery = true)
    Page<Ticket> findAllTeamTicketsFullFiltered(
            @Param("priorities") List<String> priorities,
            @Param("filterProductIds") List<Long> filterProductIds,
            @Param("searchPattern") String searchPattern,
            @Param("slaStatuses") List<String> slaStatuses,
            @Param("agentFilterActive") Boolean agentFilterActive,
            @Param("agentIds")      List<String> agentIds,
            @Param("topicFilterActive") Boolean topicFilterActive,
            @Param("topicIds")      List<Long> topicIds,
            @Param("dateFrom")      ZonedDateTime dateFrom,
            @Param("dateTo")        ZonedDateTime dateTo,
            Pageable pageable);

    /** Product tickets — agent/admin + all filters. */
    @Query(value = """
        SELECT * FROM tickets t
        WHERE t.product_id = CAST(:productId AS bigint)
          AND (t.status IN (:statuses))
          AND (t.priority IN (:priorities))
          AND (CAST(:searchPattern AS text) IS NULL OR LOWER(t.title) LIKE CAST(:searchPattern AS text))
          AND (CAST(:dateFrom AS timestamptz) IS NULL OR t.created_at >= CAST(:dateFrom AS timestamptz))
          AND (CAST(:dateTo AS timestamptz) IS NULL OR t.created_at <= CAST(:dateTo AS timestamptz))
          AND (('BREACHED' IN (:slaStatuses) AND t.sla_breached = true OR 'ACTIVE' IN (:slaStatuses) AND t.sla_breached = false AND t.sla_paused_at IS NULL OR 'PAUSED' IN (:slaStatuses) AND t.sla_breached = false AND t.sla_paused_at IS NOT NULL))
          AND (:agentFilterActive = FALSE
               OR EXISTS (SELECT 1 FROM ticket_claims tc WHERE tc.ticket_id = t.id AND tc.agent_id IN (:agentIds)))
          AND (:topicFilterActive = FALSE OR t.topic_id IN (:topicIds))
        """, nativeQuery = true)
    Page<Ticket> findByProductIdFullFiltered(
            @Param("productId")     Long productId,
            @Param("statuses") List<String> statuses,
            @Param("priorities") List<String> priorities,
            @Param("searchPattern") String searchPattern,
            @Param("slaStatuses") List<String> slaStatuses,
            @Param("agentFilterActive") Boolean agentFilterActive,
            @Param("agentIds")      List<String> agentIds,
            @Param("topicFilterActive") Boolean topicFilterActive,
            @Param("topicIds")      List<Long> topicIds,
            @Param("dateFrom")      ZonedDateTime dateFrom,
            @Param("dateTo")        ZonedDateTime dateTo,
            Pageable pageable);

    /** Product tickets — customer + all filters. */
    @Query(value = """
        SELECT * FROM tickets t
        WHERE t.product_id = CAST(:productId AS bigint)
          AND t.customer_id = CAST(:customerId AS text)
          AND (t.status IN (:statuses))
          AND (t.priority IN (:priorities))
          AND (CAST(:searchPattern AS text) IS NULL OR LOWER(t.title) LIKE CAST(:searchPattern AS text))
          AND (CAST(:dateFrom AS timestamptz) IS NULL OR t.created_at >= CAST(:dateFrom AS timestamptz))
          AND (CAST(:dateTo AS timestamptz) IS NULL OR t.created_at <= CAST(:dateTo AS timestamptz))
          AND (('BREACHED' IN (:slaStatuses) AND t.sla_breached = true OR 'ACTIVE' IN (:slaStatuses) AND t.sla_breached = false AND t.sla_paused_at IS NULL OR 'PAUSED' IN (:slaStatuses) AND t.sla_breached = false AND t.sla_paused_at IS NOT NULL))
          AND (:agentFilterActive = FALSE
               OR EXISTS (SELECT 1 FROM ticket_claims tc WHERE tc.ticket_id = t.id AND tc.agent_id IN (:agentIds)))
          AND (:topicFilterActive = FALSE OR t.topic_id IN (:topicIds))
        """, nativeQuery = true)
    Page<Ticket> findByProductIdAndCustomerIdFullFiltered(
            @Param("productId")     Long productId,
            @Param("customerId")    String customerId,
            @Param("statuses") List<String> statuses,
            @Param("priorities") List<String> priorities,
            @Param("searchPattern") String searchPattern,
            @Param("slaStatuses") List<String> slaStatuses,
            @Param("agentFilterActive") Boolean agentFilterActive,
            @Param("agentIds")      List<String> agentIds,
            @Param("topicFilterActive") Boolean topicFilterActive,
            @Param("topicIds")      List<Long> topicIds,
            @Param("dateFrom")      ZonedDateTime dateFrom,
            @Param("dateTo")        ZonedDateTime dateTo,
            Pageable pageable);

    /** Customer tickets — status + priority filtered paging (legacy JPQL variant). */
    @Query("""
        SELECT t FROM Ticket t
        WHERE t.customerId = :customerId
          AND (:statuses IS NULL OR t.status IN :statuses)
          AND (:priorities IS NULL OR t.priority IN :priorities)
        """)
    Page<Ticket> findByCustomerIdFiltered(
            @Param("customerId") String customerId,
            @Param("statuses") List<String> statuses,
            @Param("priorities") List<String> priorities,
            Pageable pageable);

    /**
     * Customer tickets — paged in SLA urgency order.
     * Group 0: overdue ({@code slaBreached=true}) — deadline ASC (longest overdue = most urgent).
     * Group 1: timer running ({@code slaBreached=false}, not paused) — deadline ASC (least time remaining = urgent).
     * Group 2: paused ({@code slaPausedAt IS NOT NULL}) — deadline ASC.
     */
    @Query("""
        SELECT t FROM Ticket t
        WHERE t.customerId = :customerId
          AND (:statuses IS NULL OR t.status IN :statuses)
          AND (:priorities IS NULL OR t.priority IN :priorities)
        ORDER BY
          CASE WHEN t.slaBreached = true THEN 0
               WHEN t.slaPausedAt IS NULL THEN 1
               ELSE 2 END ASC,
          t.slaDeadline ASC NULLS LAST
        """)
    Page<Ticket> findByCustomerIdFilteredOrderBySlaUrgencyAsc(
            @Param("customerId") String customerId,
            @Param("statuses") List<String> statuses,
            @Param("priorities") List<String> priorities,
            Pageable pageable);

    @Query("""
        SELECT t FROM Ticket t
        WHERE t.customerId = :customerId
          AND (:statuses IS NULL OR t.status IN :statuses)
          AND (:priorities IS NULL OR t.priority IN :priorities)
        ORDER BY
          CASE WHEN t.slaBreached = true THEN 0
               WHEN t.slaPausedAt IS NULL THEN 1
               ELSE 2 END DESC,
          t.slaDeadline DESC NULLS LAST
        """)
    Page<Ticket> findByCustomerIdFilteredOrderBySlaUrgencyDesc(
            @Param("customerId") String customerId,
            @Param("statuses") List<String> statuses,
            @Param("priorities") List<String> priorities,
            Pageable pageable);
    @Query("""
        SELECT t FROM Ticket t
        WHERE t.customerId = :customerId
          AND (:statuses IS NULL OR t.status IN :statuses)
          AND (:priorities IS NULL OR t.priority IN :priorities)
        ORDER BY
          CASE t.priority WHEN 'CRITICAL' THEN 1 WHEN 'HIGH' THEN 2 WHEN 'MEDIUM' THEN 3 WHEN 'LOW' THEN 4 ELSE 5 END ASC
        """)
    Page<Ticket> findByCustomerIdFilteredOrderByPriorityAsc(
            @Param("customerId") String customerId,
            @Param("statuses") List<String> statuses,
            @Param("priorities") List<String> priorities,
            Pageable pageable);

    @Query("""
        SELECT t FROM Ticket t
        WHERE t.customerId = :customerId
          AND (:statuses IS NULL OR t.status IN :statuses)
          AND (:priorities IS NULL OR t.priority IN :priorities)
        ORDER BY
          CASE t.priority WHEN 'CRITICAL' THEN 1 WHEN 'HIGH' THEN 2 WHEN 'MEDIUM' THEN 3 WHEN 'LOW' THEN 4 ELSE 5 END DESC
        """)
    Page<Ticket> findByCustomerIdFilteredOrderByPriorityDesc(
            @Param("customerId") String customerId,
            @Param("statuses") List<String> statuses,
            @Param("priorities") List<String> priorities,
            Pageable pageable);

    /** Pool (NEW) tickets — paged by priority filter, scoped to the agent's authorized products. */
    @Query("""
        SELECT t FROM Ticket t
        WHERE t.status = 'NEW'
          AND t.productId IN :productIds
          AND (:priorities IS NULL OR t.priority IN :priorities)
        """)
    Page<Ticket> findPoolTicketsFiltered(
            @Param("productIds") List<Long> productIds,
            @Param("priorities") List<String> priorities,
            Pageable pageable);

    @Query("""
        SELECT t FROM Ticket t
        WHERE t.status = 'NEW'
          AND t.productId IN :productIds
          AND (:priorities IS NULL OR t.priority IN :priorities)
        ORDER BY
          CASE t.priority WHEN 'CRITICAL' THEN 1 WHEN 'HIGH' THEN 2 WHEN 'MEDIUM' THEN 3 WHEN 'LOW' THEN 4 ELSE 5 END ASC
        """)
    Page<Ticket> findPoolTicketsFilteredOrderByPriorityAsc(
            @Param("productIds") List<Long> productIds,
            @Param("priorities") List<String> priorities,
            Pageable pageable);

    @Query("""
        SELECT t FROM Ticket t
        WHERE t.status = 'NEW'
          AND t.productId IN :productIds
          AND (:priorities IS NULL OR t.priority IN :priorities)
        ORDER BY
          CASE t.priority WHEN 'CRITICAL' THEN 1 WHEN 'HIGH' THEN 2 WHEN 'MEDIUM' THEN 3 WHEN 'LOW' THEN 4 ELSE 5 END DESC
        """)
    Page<Ticket> findPoolTicketsFilteredOrderByPriorityDesc(
            @Param("productIds") List<Long> productIds,
            @Param("priorities") List<String> priorities,
            Pageable pageable);

    /** Pool (NEW) tickets — paged in SLA urgency order (see {@link #findByCustomerIdFilteredOrderBySlaUrgencyAsc}). */
    @Query("""
        SELECT t FROM Ticket t
        WHERE t.status = 'NEW'
          AND t.productId IN :productIds
          AND (:priorities IS NULL OR t.priority IN :priorities)
        ORDER BY
          CASE WHEN t.slaBreached = true THEN 0
               WHEN t.slaPausedAt IS NULL THEN 1
               ELSE 2 END ASC,
          t.slaDeadline ASC NULLS LAST
        """)
    Page<Ticket> findPoolTicketsFilteredOrderBySlaUrgencyAsc(
            @Param("productIds") List<Long> productIds,
            @Param("priorities") List<String> priorities,
            Pageable pageable);

    @Query("""
        SELECT t FROM Ticket t
        WHERE t.status = 'NEW'
          AND t.productId IN :productIds
          AND (:priorities IS NULL OR t.priority IN :priorities)
        ORDER BY
          CASE WHEN t.slaBreached = true THEN 0
               WHEN t.slaPausedAt IS NULL THEN 1
               ELSE 2 END DESC,
          t.slaDeadline DESC NULLS LAST
        """)
    Page<Ticket> findPoolTicketsFilteredOrderBySlaUrgencyDesc(
            @Param("productIds") List<Long> productIds,
            @Param("priorities") List<String> priorities,
            Pageable pageable);
    @Query("""
        SELECT t FROM Ticket t
        WHERE t.status = 'NEW'
          AND (:priorities IS NULL OR t.priority IN :priorities)
        """)
    Page<Ticket> findAllPoolTicketsFiltered(
            @Param("priorities") List<String> priorities,
            Pageable pageable);

    @Query("""
        SELECT t FROM Ticket t
        WHERE t.status = 'NEW'
          AND (:priorities IS NULL OR t.priority IN :priorities)
        ORDER BY
          CASE t.priority WHEN 'CRITICAL' THEN 1 WHEN 'HIGH' THEN 2 WHEN 'MEDIUM' THEN 3 WHEN 'LOW' THEN 4 ELSE 5 END ASC
        """)
    Page<Ticket> findAllPoolTicketsFilteredOrderByPriorityAsc(
            @Param("priorities") List<String> priorities,
            Pageable pageable);

    @Query("""
        SELECT t FROM Ticket t
        WHERE t.status = 'NEW'
          AND (:priorities IS NULL OR t.priority IN :priorities)
        ORDER BY
          CASE t.priority WHEN 'CRITICAL' THEN 1 WHEN 'HIGH' THEN 2 WHEN 'MEDIUM' THEN 3 WHEN 'LOW' THEN 4 ELSE 5 END DESC
        """)
    Page<Ticket> findAllPoolTicketsFilteredOrderByPriorityDesc(
            @Param("priorities") List<String> priorities,
            Pageable pageable);

    /**
     * Tickets claimed by the agent — the caller passes in the ID list obtained from
     * a {@code ticket_claims} query; this method only pages the ticket side.
     */
    @Query("""
        SELECT t FROM Ticket t
        WHERE t.id IN :ticketIds
          AND (:statuses IS NULL OR t.status IN :statuses)
          AND (:priorities IS NULL OR t.priority IN :priorities)
        """)
    Page<Ticket> findClaimedTicketsFiltered(
            @Param("ticketIds") List<Long> ticketIds,
            @Param("statuses") List<String> statuses,
            @Param("priorities") List<String> priorities,
            Pageable pageable);

    @Query("""
        SELECT t FROM Ticket t
        WHERE t.id IN :ticketIds
          AND (:statuses IS NULL OR t.status IN :statuses)
          AND (:priorities IS NULL OR t.priority IN :priorities)
        ORDER BY
          CASE t.priority WHEN 'CRITICAL' THEN 1 WHEN 'HIGH' THEN 2 WHEN 'MEDIUM' THEN 3 WHEN 'LOW' THEN 4 ELSE 5 END ASC
        """)
    Page<Ticket> findClaimedTicketsFilteredOrderByPriorityAsc(
            @Param("ticketIds") List<Long> ticketIds,
            @Param("statuses") List<String> statuses,
            @Param("priorities") List<String> priorities,
            Pageable pageable);

    @Query("""
        SELECT t FROM Ticket t
        WHERE t.id IN :ticketIds
          AND (:statuses IS NULL OR t.status IN :statuses)
          AND (:priorities IS NULL OR t.priority IN :priorities)
        ORDER BY
          CASE t.priority WHEN 'CRITICAL' THEN 1 WHEN 'HIGH' THEN 2 WHEN 'MEDIUM' THEN 3 WHEN 'LOW' THEN 4 ELSE 5 END DESC
        """)
    Page<Ticket> findClaimedTicketsFilteredOrderByPriorityDesc(
            @Param("ticketIds") List<Long> ticketIds,
            @Param("statuses") List<String> statuses,
            @Param("priorities") List<String> priorities,
            Pageable pageable);

    /** Team tickets — paged with status + priority filters, scoped to the agent's authorized products. */
    @Query("""
        SELECT t FROM Ticket t
        WHERE t.productId IN :productIds
          AND t.status IN :statuses
          AND (:priorities IS NULL OR t.priority IN :priorities)
        """)
    Page<Ticket> findTeamTicketsFiltered(
            @Param("productIds") List<Long> productIds,
            @Param("statuses") List<String> statuses,
            @Param("priorities") List<String> priorities,
            Pageable pageable);

    @Query("""
        SELECT t FROM Ticket t
        WHERE t.productId IN :productIds
          AND t.status IN :statuses
          AND (:priorities IS NULL OR t.priority IN :priorities)
        ORDER BY
          CASE t.priority WHEN 'CRITICAL' THEN 1 WHEN 'HIGH' THEN 2 WHEN 'MEDIUM' THEN 3 WHEN 'LOW' THEN 4 ELSE 5 END ASC
        """)
    Page<Ticket> findTeamTicketsFilteredOrderByPriorityAsc(
            @Param("productIds") List<Long> productIds,
            @Param("statuses") List<String> statuses,
            @Param("priorities") List<String> priorities,
            Pageable pageable);

    @Query("""
        SELECT t FROM Ticket t
        WHERE t.productId IN :productIds
          AND t.status IN :statuses
          AND (:priorities IS NULL OR t.priority IN :priorities)
        ORDER BY
          CASE t.priority WHEN 'CRITICAL' THEN 1 WHEN 'HIGH' THEN 2 WHEN 'MEDIUM' THEN 3 WHEN 'LOW' THEN 4 ELSE 5 END DESC
        """)
    Page<Ticket> findTeamTicketsFilteredOrderByPriorityDesc(
            @Param("productIds") List<Long> productIds,
            @Param("statuses") List<String> statuses,
            @Param("priorities") List<String> priorities,
            Pageable pageable);

    /** Team tickets — pages active tickets (excluding NEW/CLOSED) across all products for ADMIN/MANAGER. */
    @Query("""
        SELECT t FROM Ticket t
        WHERE t.status NOT IN ('NEW', 'CLOSED')
          AND (:priorities IS NULL OR t.priority IN :priorities)
        """)
    Page<Ticket> findAllTeamTicketsFiltered(
            @Param("priorities") List<String> priorities,
            Pageable pageable);

    @Query("""
        SELECT t FROM Ticket t
        WHERE t.status NOT IN ('NEW', 'CLOSED')
          AND (:priorities IS NULL OR t.priority IN :priorities)
        ORDER BY
          CASE t.priority WHEN 'CRITICAL' THEN 1 WHEN 'HIGH' THEN 2 WHEN 'MEDIUM' THEN 3 WHEN 'LOW' THEN 4 ELSE 5 END ASC
        """)
    Page<Ticket> findAllTeamTicketsFilteredOrderByPriorityAsc(
            @Param("priorities") List<String> priorities,
            Pageable pageable);

    @Query("""
        SELECT t FROM Ticket t
        WHERE t.status NOT IN ('NEW', 'CLOSED')
          AND (:priorities IS NULL OR t.priority IN :priorities)
        ORDER BY
          CASE t.priority WHEN 'CRITICAL' THEN 1 WHEN 'HIGH' THEN 2 WHEN 'MEDIUM' THEN 3 WHEN 'LOW' THEN 4 ELSE 5 END DESC
        """)
    Page<Ticket> findAllTeamTicketsFilteredOrderByPriorityDesc(
            @Param("priorities") List<String> priorities,
            Pageable pageable);

    /** Product tickets (agent/admin) — paged by status + priority filters for a single product. */
    @Query("""
        SELECT t FROM Ticket t
        WHERE t.productId = :productId
          AND (:statuses IS NULL OR t.status IN :statuses)
          AND (:priorities IS NULL OR t.priority IN :priorities)
        """)
    Page<Ticket> findByProductIdFiltered(
            @Param("productId") Long productId,
            @Param("statuses") List<String> statuses,
            @Param("priorities") List<String> priorities,
            Pageable pageable);

    @Query("""
        SELECT t FROM Ticket t
        WHERE t.productId = :productId
          AND (:statuses IS NULL OR t.status IN :statuses)
          AND (:priorities IS NULL OR t.priority IN :priorities)
        ORDER BY
          CASE t.priority WHEN 'CRITICAL' THEN 1 WHEN 'HIGH' THEN 2 WHEN 'MEDIUM' THEN 3 WHEN 'LOW' THEN 4 ELSE 5 END ASC
        """)
    Page<Ticket> findByProductIdFilteredOrderByPriorityAsc(
            @Param("productId") Long productId,
            @Param("statuses") List<String> statuses,
            @Param("priorities") List<String> priorities,
            Pageable pageable);

    @Query("""
        SELECT t FROM Ticket t
        WHERE t.productId = :productId
          AND (:statuses IS NULL OR t.status IN :statuses)
          AND (:priorities IS NULL OR t.priority IN :priorities)
        ORDER BY
          CASE t.priority WHEN 'CRITICAL' THEN 1 WHEN 'HIGH' THEN 2 WHEN 'MEDIUM' THEN 3 WHEN 'LOW' THEN 4 ELSE 5 END DESC
        """)
    Page<Ticket> findByProductIdFilteredOrderByPriorityDesc(
            @Param("productId") Long productId,
            @Param("statuses") List<String> statuses,
            @Param("priorities") List<String> priorities,
            Pageable pageable);

    /** Product tickets (customer view) — paged per product, restricted to the given customer. */
    @Query("""
        SELECT t FROM Ticket t
        WHERE t.productId = :productId
          AND t.customerId = :customerId
          AND (:statuses IS NULL OR t.status IN :statuses)
          AND (:priorities IS NULL OR t.priority IN :priorities)
        """)
    Page<Ticket> findByProductIdAndCustomerIdFiltered(
            @Param("productId") Long productId,
            @Param("customerId") String customerId,
            @Param("statuses") List<String> statuses,
            @Param("priorities") List<String> priorities,
            Pageable pageable);

    @Query("""
        SELECT t FROM Ticket t
        WHERE t.productId = :productId
          AND t.customerId = :customerId
          AND (:statuses IS NULL OR t.status IN :statuses)
          AND (:priorities IS NULL OR t.priority IN :priorities)
        ORDER BY
          CASE t.priority WHEN 'CRITICAL' THEN 1 WHEN 'HIGH' THEN 2 WHEN 'MEDIUM' THEN 3 WHEN 'LOW' THEN 4 ELSE 5 END ASC
        """)
    Page<Ticket> findByProductIdAndCustomerIdFilteredOrderByPriorityAsc(
            @Param("productId") Long productId,
            @Param("customerId") String customerId,
            @Param("statuses") List<String> statuses,
            @Param("priorities") List<String> priorities,
            Pageable pageable);

    @Query("""
        SELECT t FROM Ticket t
        WHERE t.productId = :productId
          AND t.customerId = :customerId
          AND (:statuses IS NULL OR t.status IN :statuses)
          AND (:priorities IS NULL OR t.priority IN :priorities)
        ORDER BY
          CASE t.priority WHEN 'CRITICAL' THEN 1 WHEN 'HIGH' THEN 2 WHEN 'MEDIUM' THEN 3 WHEN 'LOW' THEN 4 ELSE 5 END DESC
        """)
    Page<Ticket> findByProductIdAndCustomerIdFilteredOrderByPriorityDesc(
            @Param("productId") Long productId,
            @Param("customerId") String customerId,
            @Param("statuses") List<String> statuses,
            @Param("priorities") List<String> priorities,
            Pageable pageable);

    // Havuz (NEW) biletleri — ADMIN/MANAGER icin tum urunler, SLA urgency sirasi ile
    @Query("""
        SELECT t FROM Ticket t
        WHERE t.status = 'NEW'
          AND (:priorities IS NULL OR t.priority IN :priorities)
        ORDER BY
          CASE WHEN t.slaBreached = true THEN 0
               WHEN t.slaPausedAt IS NULL THEN 1
               ELSE 2 END ASC,
          t.slaDeadline ASC NULLS LAST
        """)
    Page<Ticket> findAllPoolTicketsFilteredOrderBySlaUrgencyAsc(
            @Param("priorities") List<String> priorities,
            Pageable pageable);

    @Query("""
        SELECT t FROM Ticket t
        WHERE t.status = 'NEW'
          AND (:priorities IS NULL OR t.priority IN :priorities)
        ORDER BY
          CASE WHEN t.slaBreached = true THEN 0
               WHEN t.slaPausedAt IS NULL THEN 1
               ELSE 2 END DESC,
          t.slaDeadline DESC NULLS LAST
        """)
    Page<Ticket> findAllPoolTicketsFilteredOrderBySlaUrgencyDesc(
            @Param("priorities") List<String> priorities,
            Pageable pageable);

    // Ajanin claim aldigi biletler — SLA urgency sirasi ile
    @Query("""
        SELECT t FROM Ticket t
        WHERE t.id IN :ticketIds
          AND (:statuses IS NULL OR t.status IN :statuses)
          AND (:priorities IS NULL OR t.priority IN :priorities)
        ORDER BY
          CASE WHEN t.slaBreached = true THEN 0
               WHEN t.slaPausedAt IS NULL THEN 1
               ELSE 2 END ASC,
          t.slaDeadline ASC NULLS LAST
        """)
    Page<Ticket> findClaimedTicketsFilteredOrderBySlaUrgencyAsc(
            @Param("ticketIds") List<Long> ticketIds,
            @Param("statuses") List<String> statuses,
            @Param("priorities") List<String> priorities,
            Pageable pageable);

    @Query("""
        SELECT t FROM Ticket t
        WHERE t.id IN :ticketIds
          AND (:statuses IS NULL OR t.status IN :statuses)
          AND (:priorities IS NULL OR t.priority IN :priorities)
        ORDER BY
          CASE WHEN t.slaBreached = true THEN 0
               WHEN t.slaPausedAt IS NULL THEN 1
               ELSE 2 END DESC,
          t.slaDeadline DESC NULLS LAST
        """)
    Page<Ticket> findClaimedTicketsFilteredOrderBySlaUrgencyDesc(
            @Param("ticketIds") List<Long> ticketIds,
            @Param("statuses") List<String> statuses,
            @Param("priorities") List<String> priorities,
            Pageable pageable);

    // Takim biletleri — yetkili urunler, SLA urgency sirasi ile
    @Query("""
        SELECT t FROM Ticket t
        WHERE t.productId IN :productIds
          AND t.status IN :statuses
          AND (:priorities IS NULL OR t.priority IN :priorities)
        ORDER BY
          CASE WHEN t.slaBreached = true THEN 0
               WHEN t.slaPausedAt IS NULL THEN 1
               ELSE 2 END ASC,
          t.slaDeadline ASC NULLS LAST
        """)
    Page<Ticket> findTeamTicketsFilteredOrderBySlaUrgencyAsc(
            @Param("productIds") List<Long> productIds,
            @Param("statuses") List<String> statuses,
            @Param("priorities") List<String> priorities,
            Pageable pageable);

    @Query("""
        SELECT t FROM Ticket t
        WHERE t.productId IN :productIds
          AND t.status IN :statuses
          AND (:priorities IS NULL OR t.priority IN :priorities)
        ORDER BY
          CASE WHEN t.slaBreached = true THEN 0
               WHEN t.slaPausedAt IS NULL THEN 1
               ELSE 2 END DESC,
          t.slaDeadline DESC NULLS LAST
        """)
    Page<Ticket> findTeamTicketsFilteredOrderBySlaUrgencyDesc(
            @Param("productIds") List<Long> productIds,
            @Param("statuses") List<String> statuses,
            @Param("priorities") List<String> priorities,
            Pageable pageable);

    // Takim biletleri — ADMIN/MANAGER icin tum urunler, SLA urgency sirasi ile
    @Query("""
        SELECT t FROM Ticket t
        WHERE t.status NOT IN ('NEW', 'CLOSED')
          AND (:priorities IS NULL OR t.priority IN :priorities)
        ORDER BY
          CASE WHEN t.slaBreached = true THEN 0
               WHEN t.slaPausedAt IS NULL THEN 1
               ELSE 2 END ASC,
          t.slaDeadline ASC NULLS LAST
        """)
    Page<Ticket> findAllTeamTicketsFilteredOrderBySlaUrgencyAsc(
            @Param("priorities") List<String> priorities,
            Pageable pageable);

    @Query("""
        SELECT t FROM Ticket t
        WHERE t.status NOT IN ('NEW', 'CLOSED')
          AND (:priorities IS NULL OR t.priority IN :priorities)
        ORDER BY
          CASE WHEN t.slaBreached = true THEN 0
               WHEN t.slaPausedAt IS NULL THEN 1
               ELSE 2 END DESC,
          t.slaDeadline DESC NULLS LAST
        """)
    Page<Ticket> findAllTeamTicketsFilteredOrderBySlaUrgencyDesc(
            @Param("priorities") List<String> priorities,
            Pageable pageable);

    // Urun biletleri — agent/admin icin SLA urgency sirasi ile
    @Query("""
        SELECT t FROM Ticket t
        WHERE t.productId = :productId
          AND (:statuses IS NULL OR t.status IN :statuses)
          AND (:priorities IS NULL OR t.priority IN :priorities)
        ORDER BY
          CASE WHEN t.slaBreached = true THEN 0
               WHEN t.slaPausedAt IS NULL THEN 1
               ELSE 2 END ASC,
          t.slaDeadline ASC NULLS LAST
        """)
    Page<Ticket> findByProductIdFilteredOrderBySlaUrgencyAsc(
            @Param("productId") Long productId,
            @Param("statuses") List<String> statuses,
            @Param("priorities") List<String> priorities,
            Pageable pageable);

    @Query("""
        SELECT t FROM Ticket t
        WHERE t.productId = :productId
          AND (:statuses IS NULL OR t.status IN :statuses)
          AND (:priorities IS NULL OR t.priority IN :priorities)
        ORDER BY
          CASE WHEN t.slaBreached = true THEN 0
               WHEN t.slaPausedAt IS NULL THEN 1
               ELSE 2 END DESC,
          t.slaDeadline DESC NULLS LAST
        """)
    Page<Ticket> findByProductIdFilteredOrderBySlaUrgencyDesc(
            @Param("productId") Long productId,
            @Param("statuses") List<String> statuses,
            @Param("priorities") List<String> priorities,
            Pageable pageable);

    // Urun biletleri — musteri icin SLA urgency sirasi ile
    @Query("""
        SELECT t FROM Ticket t
        WHERE t.productId = :productId
          AND t.customerId = :customerId
          AND (:statuses IS NULL OR t.status IN :statuses)
          AND (:priorities IS NULL OR t.priority IN :priorities)
        ORDER BY
          CASE WHEN t.slaBreached = true THEN 0
               WHEN t.slaPausedAt IS NULL THEN 1
               ELSE 2 END ASC,
          t.slaDeadline ASC NULLS LAST
        """)
    Page<Ticket> findByProductIdAndCustomerIdFilteredOrderBySlaUrgencyAsc(
            @Param("productId") Long productId,
            @Param("customerId") String customerId,
            @Param("statuses") List<String> statuses,
            @Param("priorities") List<String> priorities,
            Pageable pageable);

    @Query("""
        SELECT t FROM Ticket t
        WHERE t.productId = :productId
          AND t.customerId = :customerId
          AND (:statuses IS NULL OR t.status IN :statuses)
          AND (:priorities IS NULL OR t.priority IN :priorities)
        ORDER BY
          CASE WHEN t.slaBreached = true THEN 0
               WHEN t.slaPausedAt IS NULL THEN 1
               ELSE 2 END DESC,
          t.slaDeadline DESC NULLS LAST
        """)
    Page<Ticket> findByProductIdAndCustomerIdFilteredOrderBySlaUrgencyDesc(
            @Param("productId") Long productId,
            @Param("customerId") String customerId,
            @Param("statuses") List<String> statuses,
            @Param("priorities") List<String> priorities,
            Pageable pageable);

    // -------------------------------------------------------------------------

    /** Distribution of all tickets by status: each row is {@code [status, count]}. */
    @Query("SELECT t.status, COUNT(t) FROM Ticket t GROUP BY t.status")
    List<Object[]> countTicketsGroupedByStatus();

    /** Product-scoped variant of {@link #countTicketsGroupedByStatus}. */
    @Query("SELECT t.status, COUNT(t) FROM Ticket t "
         + "WHERE (:filterByProduct = false OR t.productId IN :productIds) GROUP BY t.status")
    List<Object[]> countTicketsGroupedByStatusScoped(@Param("filterByProduct") boolean filterByProduct,
                                                     @Param("productIds") List<Long> productIds);

    /** Total ticket count for the given statuses (typically the "open" status list). */
    @Query("SELECT COUNT(t) FROM Ticket t WHERE t.status IN :statuses")
    Long countByStatusIn(@Param("statuses") List<String> statuses);

    /** Product-scoped variant of {@link #countByStatusIn}. */
    @Query("SELECT COUNT(t) FROM Ticket t WHERE t.status IN :statuses "
         + "AND (:filterByProduct = false OR t.productId IN :productIds)")
    Long countByStatusInScoped(@Param("statuses") List<String> statuses,
                               @Param("filterByProduct") boolean filterByProduct,
                               @Param("productIds") List<Long> productIds);

    /** Count of SLA-breached tickets among those in the given statuses. */
    @Query("SELECT COUNT(t) FROM Ticket t WHERE t.status IN :statuses AND t.slaBreached = true")
    Long countSlaBreachedByStatusIn(@Param("statuses") List<String> statuses);

    /** Product-scoped variant of {@link #countSlaBreachedByStatusIn}. */
    @Query("SELECT COUNT(t) FROM Ticket t WHERE t.status IN :statuses AND t.slaBreached = true "
         + "AND (:filterByProduct = false OR t.productId IN :productIds)")
    Long countSlaBreachedByStatusInScoped(@Param("statuses") List<String> statuses,
                                          @Param("filterByProduct") boolean filterByProduct,
                                          @Param("productIds") List<Long> productIds);

    /** Count of tickets created since the given date that match the status filter (for KPIs). */
    @Query("SELECT COUNT(t) FROM Ticket t WHERE t.status IN :statuses AND t.createdAt >= :since")
    Long countCreatedSinceByStatusIn(@Param("statuses") List<String> statuses,
                                      @Param("since") java.time.ZonedDateTime since);

    /** Product-scoped variant of {@link #countCreatedSinceByStatusIn}. */
    @Query("SELECT COUNT(t) FROM Ticket t WHERE t.status IN :statuses AND t.createdAt >= :since "
         + "AND (:filterByProduct = false OR t.productId IN :productIds)")
    Long countCreatedSinceByStatusInScoped(@Param("statuses") List<String> statuses,
                                           @Param("since") java.time.ZonedDateTime since,
                                           @Param("filterByProduct") boolean filterByProduct,
                                           @Param("productIds") List<Long> productIds);

    /** Priority-based distribution of tickets in the given statuses: each row is {@code [priority, count]}. */
    @Query("SELECT t.priority, COUNT(t) FROM Ticket t WHERE t.status IN :statuses GROUP BY t.priority")
    List<Object[]> countByStatusInGroupByPriority(@Param("statuses") List<String> statuses);

    /** Product-scoped variant of {@link #countByStatusInGroupByPriority}. */
    @Query("SELECT t.priority, COUNT(t) FROM Ticket t WHERE t.status IN :statuses "
         + "AND (:filterByProduct = false OR t.productId IN :productIds) GROUP BY t.priority")
    List<Object[]> countByStatusInGroupByPriorityScoped(@Param("statuses") List<String> statuses,
                                                        @Param("filterByProduct") boolean filterByProduct,
                                                        @Param("productIds") List<Long> productIds);

    /** Average resolution time for RESOLVED tickets (hours) — all time. */
    @Query(value = "SELECT AVG(EXTRACT(EPOCH FROM (t.resolved_at - t.created_at)) / 3600.0) FROM tickets t WHERE t.status = 'RESOLVED' AND t.created_at IS NOT NULL AND t.resolved_at IS NOT NULL", nativeQuery = true)
    Double findAvgResolutionHoursForResolved();

    /** Product-scoped variant of {@link #findAvgResolutionHoursForResolved}. */
    @Query(value = "SELECT AVG(EXTRACT(EPOCH FROM (t.resolved_at - t.created_at)) / 3600.0) FROM tickets t "
         + "WHERE t.status = 'RESOLVED' AND t.created_at IS NOT NULL AND t.resolved_at IS NOT NULL "
         + "AND (:filterByProduct = false OR t.product_id IN (:productIds))", nativeQuery = true)
    Double findAvgResolutionHoursForResolvedScoped(@Param("filterByProduct") boolean filterByProduct,
                                                   @Param("productIds") List<Long> productIds);

    /** Total tickets created since the given date. */
    @Query("SELECT COUNT(t) FROM Ticket t WHERE t.createdAt >= :since")
    long countCreatedSince(@Param("since") ZonedDateTime since);

    /** Number of tickets transitioned to RESOLVED since the given date. */
    @Query("SELECT COUNT(t) FROM Ticket t WHERE t.status = 'RESOLVED' AND t.resolvedAt >= :since")
    long countResolvedSince(@Param("since") ZonedDateTime since);

    /** Number of tickets moved to CLOSED since the given date. */
    @Query("SELECT COUNT(t) FROM Ticket t WHERE t.status = 'CLOSED' AND t.closedAt >= :since")
    long countClosedSince(@Param("since") ZonedDateTime since);

    /** Average resolution time (hours) for tickets resolved since the given date. */
    @Query(value = "SELECT AVG(EXTRACT(EPOCH FROM (t.resolved_at - t.created_at)) / 3600.0) FROM tickets t WHERE t.status = 'RESOLVED' AND t.resolved_at >= :since AND t.created_at IS NOT NULL AND t.resolved_at IS NOT NULL", nativeQuery = true)
    Double avgResolutionHoursSince(@Param("since") ZonedDateTime since);

    /** SLA compliance rate (%) for tickets resolved within the given window — the percentage that did not breach. */
    @Query(value = "SELECT (COUNT(CASE WHEN t.sla_breached = false THEN 1 END) * 100.0) / NULLIF(COUNT(t.id), 0) FROM tickets t WHERE t.status = 'RESOLVED' AND t.resolved_at >= :since", nativeQuery = true)
    Double slaComplianceRateSince(@Param("since") ZonedDateTime since);

    /**
     * B-9: collapses four separate COUNT/AVG queries for getWorklogCompletion into
     * a single SQL statement. PostgreSQL's FILTER syntax does the conditional
     * aggregation; a single table scan suffices.
     *
     * Returns one row; positions: [0]=totalCreated, [1]=totalResolved, [2]=totalClosed,
     * [3]=avgResolutionHours, [4]=slaComplianceRate.
     */
    @Query(value = """
            SELECT
                COUNT(*) FILTER (WHERE t.created_at >= :since)::BIGINT AS total_created,
                COUNT(*) FILTER (WHERE t.status = 'RESOLVED' AND t.resolved_at >= :since)::BIGINT AS total_resolved,
                COUNT(*) FILTER (WHERE t.status = 'CLOSED' AND t.closed_at >= :since)::BIGINT AS total_closed,
                AVG(EXTRACT(EPOCH FROM (t.resolved_at - t.created_at)) / 3600.0)
                    FILTER (WHERE t.status = 'RESOLVED' AND t.resolved_at >= :since
                            AND t.created_at IS NOT NULL AND t.resolved_at IS NOT NULL) AS avg_resolution_hours,
                (COUNT(*) FILTER (WHERE t.status = 'RESOLVED' AND t.resolved_at >= :since AND t.sla_breached = false) * 100.0)
                    / NULLIF(COUNT(*) FILTER (WHERE t.status = 'RESOLVED' AND t.resolved_at >= :since), 0) AS sla_compliance_rate
            FROM tickets t
            """, nativeQuery = true)
    List<Object[]> findWorklogCompletionAggregates(@Param("since") ZonedDateTime since);

    /** Product-scoped variant of {@link #findWorklogCompletionAggregates}. */
    @Query(value = """
            SELECT
                COUNT(*) FILTER (WHERE t.created_at >= :since)::BIGINT AS total_created,
                COUNT(*) FILTER (WHERE t.status = 'RESOLVED' AND t.resolved_at >= :since)::BIGINT AS total_resolved,
                COUNT(*) FILTER (WHERE t.status = 'CLOSED' AND t.closed_at >= :since)::BIGINT AS total_closed,
                AVG(EXTRACT(EPOCH FROM (t.resolved_at - t.created_at)) / 3600.0)
                    FILTER (WHERE t.status = 'RESOLVED' AND t.resolved_at >= :since
                            AND t.created_at IS NOT NULL AND t.resolved_at IS NOT NULL) AS avg_resolution_hours,
                (COUNT(*) FILTER (WHERE t.status = 'RESOLVED' AND t.resolved_at >= :since AND t.sla_breached = false) * 100.0)
                    / NULLIF(COUNT(*) FILTER (WHERE t.status = 'RESOLVED' AND t.resolved_at >= :since), 0) AS sla_compliance_rate
            FROM tickets t
            WHERE (:filterByProduct = false OR t.product_id IN (:productIds))
            """, nativeQuery = true)
    List<Object[]> findWorklogCompletionAggregatesScoped(@Param("since") ZonedDateTime since,
                                                         @Param("filterByProduct") boolean filterByProduct,
                                                         @Param("productIds") List<Long> productIds);

    /** Open + SLA-breached tickets, sorted ascending by deadline (for the alert page and the scheduler). */
    @Query("SELECT t FROM Ticket t WHERE t.status IN :statuses AND t.slaBreached = true ORDER BY t.slaDeadline ASC")
    List<Ticket> findBreachedOpenTickets(@Param("statuses") List<String> statuses, Pageable pageable);

    /** Product-scoped variant of {@link #findBreachedOpenTickets}. */
    @Query("SELECT t FROM Ticket t WHERE t.status IN :statuses AND t.slaBreached = true "
         + "AND (:filterByProduct = false OR t.productId IN :productIds) ORDER BY t.slaDeadline ASC")
    List<Ticket> findBreachedOpenTicketsScoped(@Param("statuses") List<String> statuses,
                                               @Param("filterByProduct") boolean filterByProduct,
                                               @Param("productIds") List<Long> productIds,
                                               Pageable pageable);

    /** Tickets with a deadline before {@code before} that have not yet breached and are not paused. */
    @Query("SELECT t FROM Ticket t WHERE t.status IN :statuses AND t.slaBreached = false AND t.slaPausedAt IS NULL AND t.slaDeadline IS NOT NULL AND t.slaDeadline <= :before ORDER BY t.slaDeadline ASC")
    List<Ticket> findUpcomingBreachTickets(@Param("statuses") List<String> statuses, @Param("before") ZonedDateTime before, Pageable pageable);

    /** Variant of {@link #findUpcomingBreachTickets} narrowed by priority filter (for the critical-priority scheduler). */
    @Query("SELECT t FROM Ticket t WHERE t.status IN :statuses AND t.priority IN :priorities AND t.slaBreached = false AND t.slaPausedAt IS NULL AND t.slaDeadline IS NOT NULL AND t.slaDeadline <= :before ORDER BY t.slaDeadline ASC")
    List<Ticket> findUpcomingBreachTicketsByPriority(@Param("statuses") List<String> statuses, @Param("priorities") List<String> priorities, @Param("before") ZonedDateTime before, Pageable pageable);

    /** Product-scoped variant of {@link #findUpcomingBreachTicketsByPriority}. */
    @Query("SELECT t FROM Ticket t WHERE t.status IN :statuses AND t.priority IN :priorities AND t.slaBreached = false AND t.slaPausedAt IS NULL AND t.slaDeadline IS NOT NULL AND t.slaDeadline <= :before "
         + "AND (:filterByProduct = false OR t.productId IN :productIds) ORDER BY t.slaDeadline ASC")
    List<Ticket> findUpcomingBreachTicketsByPriorityScoped(@Param("statuses") List<String> statuses,
                                                           @Param("priorities") List<String> priorities,
                                                           @Param("before") ZonedDateTime before,
                                                           @Param("filterByProduct") boolean filterByProduct,
                                                           @Param("productIds") List<Long> productIds,
                                                           Pageable pageable);

    /**
     * Tickets within the warning threshold for which the "SLA approaching" email has
     * not yet been sent. The scheduler picks these up each cycle and sets
     * sla_warning_sent_at once it sends the email — so the same ticket never receives
     * the email twice.
     */
    @Query("SELECT t FROM Ticket t WHERE t.status IN :statuses AND t.priority IN :priorities "
         + "AND t.slaBreached = false AND t.slaPausedAt IS NULL "
         + "AND t.slaWarningSentAt IS NULL "
         + "AND t.slaDeadline IS NOT NULL AND t.slaDeadline <= :before "
         + "ORDER BY t.slaDeadline ASC")
    List<Ticket> findPendingWarningTicketsByPriority(@Param("statuses") List<String> statuses,
                                                     @Param("priorities") List<String> priorities,
                                                     @Param("before") ZonedDateTime before,
                                                     Pageable pageable);

    /** Tickets that have been stuck in WAITING_FOR_CUSTOMER for too long (for escalation alerts). */
    @Query("SELECT t FROM Ticket t WHERE t.status = 'WAITING_FOR_CUSTOMER' AND t.createdAt <= :since ORDER BY t.createdAt ASC")
    List<Ticket> findWaitingTooLongTickets(@Param("since") ZonedDateTime since, Pageable pageable);

    /** Product-scoped variant of {@link #findWaitingTooLongTickets}. */
    @Query("SELECT t FROM Ticket t WHERE t.status = 'WAITING_FOR_CUSTOMER' AND t.createdAt <= :since "
         + "AND (:filterByProduct = false OR t.productId IN :productIds) ORDER BY t.createdAt ASC")
    List<Ticket> findWaitingTooLongTicketsScoped(@Param("since") ZonedDateTime since,
                                                 @Param("filterByProduct") boolean filterByProduct,
                                                 @Param("productIds") List<Long> productIds,
                                                 Pageable pageable);

    /** Count of tickets with no claims — within the given status filter (typically NEW). */
    @Query("SELECT COUNT(t) FROM Ticket t WHERE t.status IN :statuses AND NOT EXISTS (SELECT 1 FROM TicketClaim tc WHERE tc.ticket = t)")
    long countUnassignedByStatusIn(@Param("statuses") List<String> statuses);

    /** Product-scoped variant of {@link #countUnassignedByStatusIn}. */
    @Query("SELECT COUNT(t) FROM Ticket t WHERE t.status IN :statuses "
         + "AND (:filterByProduct = false OR t.productId IN :productIds) "
         + "AND NOT EXISTS (SELECT 1 FROM TicketClaim tc WHERE tc.ticket = t)")
    long countUnassignedByStatusInScoped(@Param("statuses") List<String> statuses,
                                         @Param("filterByProduct") boolean filterByProduct,
                                         @Param("productIds") List<Long> productIds);

    long countByStatus(String status);

    /** Product-scoped count of tickets in the given status. */
    @Query("SELECT COUNT(t) FROM Ticket t WHERE t.status = :status "
         + "AND (:filterByProduct = false OR t.productId IN :productIds)")
    long countByStatusScoped(@Param("status") String status,
                             @Param("filterByProduct") boolean filterByProduct,
                             @Param("productIds") List<Long> productIds);

    /**
     * Tickets whose SLA deadline has passed but which are not yet stamped
     * {@code slaBreached=true}. The scheduler finds these, sets the breach flag and
     * dispatches notifications.
     */
    @Query("SELECT t FROM Ticket t WHERE t.slaBreached = false AND t.slaDeadline IS NOT NULL AND t.slaDeadline < :now AND t.status IN :statuses")
    List<Ticket> findOverdueUnmarkedTickets(@Param("now") ZonedDateTime now,
                                            @Param("statuses") List<String> statuses);

    /** Average time-in-queue (hours) for open tickets, measured from creation to now. */
    @Query(value = "SELECT AVG(EXTRACT(EPOCH FROM (CURRENT_TIMESTAMP - t.created_at)) / 3600.0) FROM tickets t WHERE t.status IN (:statuses) AND t.created_at IS NOT NULL", nativeQuery = true)
    Double avgWaitingHoursForOpen(@Param("statuses") List<String> statuses);

    /** Product-scoped variant of {@link #avgWaitingHoursForOpen}. */
    @Query(value = "SELECT AVG(EXTRACT(EPOCH FROM (CURRENT_TIMESTAMP - t.created_at)) / 3600.0) FROM tickets t "
         + "WHERE t.status IN (:statuses) AND t.created_at IS NOT NULL "
         + "AND (:filterByProduct = false OR t.product_id IN (:productIds))", nativeQuery = true)
    Double avgWaitingHoursForOpenScoped(@Param("statuses") List<String> statuses,
                                        @Param("filterByProduct") boolean filterByProduct,
                                        @Param("productIds") List<Long> productIds);

    /**
     * Daily ticket timeline for the last N days — returns per-day counts of tickets
     * created, resolved, closed and SLA-breached. PostgreSQL's {@code generate_series}
     * ensures even days without tickets appear as rows with 0 (so the UI charts have
     * no gaps).
     * Returned columns: {@code [metric_date, created, resolved, closed, sla_breach]}.
     */
    @Query(value = """
        WITH date_range AS (
            SELECT DATE(CURRENT_TIMESTAMP AT TIME ZONE 'UTC') - INTERVAL '1 day' * i AS metric_date
            FROM generate_series(0, ?1 - 1) AS i
        ),
        daily_metrics AS (
            SELECT
                COALESCE(DATE(t.created_at AT TIME ZONE 'UTC'), dr.metric_date) AS metric_date,
                COUNT(CASE WHEN DATE(t.created_at AT TIME ZONE 'UTC') = dr.metric_date THEN 1 END) AS created_count,
                COUNT(CASE WHEN DATE(t.resolved_at AT TIME ZONE 'UTC') = dr.metric_date THEN 1 END) AS resolved_count,
                COUNT(CASE WHEN DATE(t.closed_at AT TIME ZONE 'UTC') = dr.metric_date THEN 1 END) AS closed_count,
                COUNT(CASE WHEN DATE(t.created_at AT TIME ZONE 'UTC') = dr.metric_date AND t.sla_breached = true THEN 1 END) AS sla_breach_count
            FROM date_range dr
            LEFT JOIN tickets t ON
                DATE(t.created_at AT TIME ZONE 'UTC') = dr.metric_date OR
                DATE(t.resolved_at AT TIME ZONE 'UTC') = dr.metric_date OR
                DATE(t.closed_at AT TIME ZONE 'UTC') = dr.metric_date
            GROUP BY dr.metric_date, COALESCE(DATE(t.created_at AT TIME ZONE 'UTC'), dr.metric_date)
        )
        SELECT
            metric_date,
            SUM(created_count)::BIGINT,
            SUM(resolved_count)::BIGINT,
            SUM(closed_count)::BIGINT,
            SUM(sla_breach_count)::BIGINT
        FROM daily_metrics
        GROUP BY metric_date
        ORDER BY metric_date DESC
        """, nativeQuery = true)
    List<Object[]> getTicketTimelineMetrics(int days);

    /**
     * Product-scoped variant of {@link #getTicketTimelineMetrics}. The product filter is
     * pushed into the LEFT JOIN so the {@code generate_series} date scaffold is preserved
     * (days with no in-scope tickets still appear as zero rows). When {@code filterByProduct}
     * is false the predicate is a no-op and the result matches the global query.
     */
    @Query(value = """
        WITH date_range AS (
            SELECT DATE(CURRENT_TIMESTAMP AT TIME ZONE 'UTC') - INTERVAL '1 day' * i AS metric_date
            FROM generate_series(0, ?1 - 1) AS i
        ),
        daily_metrics AS (
            SELECT
                COALESCE(DATE(t.created_at AT TIME ZONE 'UTC'), dr.metric_date) AS metric_date,
                COUNT(CASE WHEN DATE(t.created_at AT TIME ZONE 'UTC') = dr.metric_date THEN 1 END) AS created_count,
                COUNT(CASE WHEN DATE(t.resolved_at AT TIME ZONE 'UTC') = dr.metric_date THEN 1 END) AS resolved_count,
                COUNT(CASE WHEN DATE(t.closed_at AT TIME ZONE 'UTC') = dr.metric_date THEN 1 END) AS closed_count,
                COUNT(CASE WHEN DATE(t.created_at AT TIME ZONE 'UTC') = dr.metric_date AND t.sla_breached = true THEN 1 END) AS sla_breach_count
            FROM date_range dr
            LEFT JOIN tickets t ON
                (DATE(t.created_at AT TIME ZONE 'UTC') = dr.metric_date OR
                 DATE(t.resolved_at AT TIME ZONE 'UTC') = dr.metric_date OR
                 DATE(t.closed_at AT TIME ZONE 'UTC') = dr.metric_date)
                AND (?2 = false OR t.product_id IN (?3))
            GROUP BY dr.metric_date, COALESCE(DATE(t.created_at AT TIME ZONE 'UTC'), dr.metric_date)
        )
        SELECT
            metric_date,
            SUM(created_count)::BIGINT,
            SUM(resolved_count)::BIGINT,
            SUM(closed_count)::BIGINT,
            SUM(sla_breach_count)::BIGINT
        FROM daily_metrics
        GROUP BY metric_date
        ORDER BY metric_date DESC
        """, nativeQuery = true)
    List<Object[]> getTicketTimelineMetricsScoped(int days, boolean filterByProduct, List<Long> productIds);

    // =========================================================================
    // Kişisel dashboard'lar — ilişki bazlı kapsam (rol değil):
    //  - Müşteri: t.customer_id = :userId (açtığı biletler)
    //  - Ajan:    ticket_claims.agent_id = :userId (claim'lediği biletler)
    // Aynı kullanıcı id'si iki bağlamda farklı ilişkiyle durabilir; bu sorgular
    // birbirine karışmaz.
    // =========================================================================

    /** Müşterinin açtığı biletlerin status dağılımı: her satır {@code [status, count]}. */
    @Query("SELECT t.status, COUNT(t) FROM Ticket t WHERE t.customerId = :customerId GROUP BY t.status")
    List<Object[]> countTicketsGroupedByStatusForCustomer(@Param("customerId") String customerId);

    /** Müşterinin açık biletlerinden SLA'sı ihlal edilmiş olanların sayısı. */
    @Query("SELECT COUNT(t) FROM Ticket t WHERE t.customerId = :customerId AND t.status IN :statuses AND t.slaBreached = true")
    Long countSlaBreachedByCustomerAndStatusIn(@Param("customerId") String customerId,
                                               @Param("statuses") List<String> statuses);

    /** Müşterinin çözüm tarihi olan (resolved/closed) biletlerinin ortalama çözüm süresi (saat). */
    @Query(value = "SELECT AVG(EXTRACT(EPOCH FROM (t.resolved_at - t.created_at)) / 3600.0) FROM tickets t "
         + "WHERE t.customer_id = CAST(:customerId AS text) AND t.resolved_at IS NOT NULL AND t.created_at IS NOT NULL", nativeQuery = true)
    Double findAvgResolutionHoursForCustomer(@Param("customerId") String customerId);

    /** Müşterinin en son açtığı biletleri (en yeni önce). Limit {@link Pageable} ile verilir. */
    @Query("SELECT t FROM Ticket t WHERE t.customerId = :customerId ORDER BY t.createdAt DESC")
    List<Ticket> findRecentByCustomerId(@Param("customerId") String customerId, Pageable pageable);

    /**
     * Müşteriye özel günlük bilet timeline'ı (son N gün). {@link #getTicketTimelineMetrics}
     * ile aynı yapı, sadece LEFT JOIN'e {@code customer_id} filtresi eklenmiştir; böylece
     * in-scope bileti olmayan günler de sıfır satırla görünür.
     * Dönen kolonlar: {@code [metric_date, created, resolved, closed, sla_breach]}.
     */
    @Query(value = """
        WITH date_range AS (
            SELECT DATE(CURRENT_TIMESTAMP AT TIME ZONE 'UTC') - INTERVAL '1 day' * i AS metric_date
            FROM generate_series(0, ?1 - 1) AS i
        ),
        daily_metrics AS (
            SELECT
                dr.metric_date AS metric_date,
                COUNT(CASE WHEN DATE(t.created_at AT TIME ZONE 'UTC') = dr.metric_date THEN 1 END) AS created_count,
                COUNT(CASE WHEN DATE(t.resolved_at AT TIME ZONE 'UTC') = dr.metric_date THEN 1 END) AS resolved_count,
                COUNT(CASE WHEN DATE(t.closed_at AT TIME ZONE 'UTC') = dr.metric_date THEN 1 END) AS closed_count,
                COUNT(CASE WHEN DATE(t.created_at AT TIME ZONE 'UTC') = dr.metric_date AND t.sla_breached = true THEN 1 END) AS sla_breach_count
            FROM date_range dr
            LEFT JOIN tickets t ON
                t.customer_id = CAST(?2 AS text)
                AND (DATE(t.created_at AT TIME ZONE 'UTC') = dr.metric_date OR
                     DATE(t.resolved_at AT TIME ZONE 'UTC') = dr.metric_date OR
                     DATE(t.closed_at AT TIME ZONE 'UTC') = dr.metric_date)
            GROUP BY dr.metric_date
        )
        SELECT metric_date, created_count::BIGINT, resolved_count::BIGINT, closed_count::BIGINT, sla_breach_count::BIGINT
        FROM daily_metrics
        ORDER BY metric_date DESC
        """, nativeQuery = true)
    List<Object[]> getCustomerTicketTimelineMetrics(int days, String customerId);

    /**
     * Ajanın claim'lediği biletlerin tek satırlık özet metrikleri.
     * Dönen kolonlar: {@code [active, resolved_24h, resolved_7d, resolved_30d,
     * sla_breached, total_claimed, avg_resolution_hours, csat_avg, csat_count]}.
     */
    @Query(value = """
            SELECT
                COUNT(CASE WHEN t.status IN ('NEW','IN_PROGRESS','WAITING_FOR_CUSTOMER') THEN 1 END)::BIGINT AS active,
                COUNT(CASE WHEN t.resolved_at >= :since24h THEN 1 END)::BIGINT AS resolved_24h,
                COUNT(CASE WHEN t.resolved_at >= :since7d  THEN 1 END)::BIGINT AS resolved_7d,
                COUNT(CASE WHEN t.resolved_at >= :since30d THEN 1 END)::BIGINT AS resolved_30d,
                COUNT(CASE WHEN t.sla_breached = true THEN 1 END)::BIGINT AS sla_breached,
                COUNT(*)::BIGINT AS total_claimed,
                COALESCE(AVG(CASE
                    WHEN t.resolved_at IS NOT NULL AND t.created_at IS NOT NULL
                        THEN EXTRACT(EPOCH FROM (t.resolved_at - t.created_at)) / 3600.0
                END), 0)::DOUBLE PRECISION AS avg_resolution_hours,
                COALESCE(AVG(CAST(cs.rating AS DOUBLE PRECISION)), 0)::DOUBLE PRECISION AS csat_avg,
                COUNT(cs.id)::BIGINT AS csat_count
            FROM ticket_claims tc
            JOIN tickets t ON t.id = tc.ticket_id
            LEFT JOIN csat_surveys cs ON cs.ticket_id = t.id
            WHERE tc.agent_id = CAST(:agentId AS text)
            """, nativeQuery = true)
    List<Object[]> findAgentSelfMetrics(@Param("agentId") String agentId,
                                        @Param("since24h") ZonedDateTime since24h,
                                        @Param("since7d") ZonedDateTime since7d,
                                        @Param("since30d") ZonedDateTime since30d);

    /** Ajanın claim'lediği biletlerin status dağılımı: her satır {@code [status, count]}. */
    @Query(value = "SELECT t.status, COUNT(*)::BIGINT FROM ticket_claims tc "
         + "JOIN tickets t ON t.id = tc.ticket_id "
         + "WHERE tc.agent_id = CAST(:agentId AS text) GROUP BY t.status", nativeQuery = true)
    List<Object[]> countClaimedTicketsGroupedByStatus(@Param("agentId") String agentId);

    /**
     * Ajana özel günlük bilet timeline'ı (son N gün) — yalnızca claim'lediği biletler.
     * "created" = claim'lediğim biletin o gün oluşturulması. Dönen kolonlar:
     * {@code [metric_date, created, resolved, closed, sla_breach]}.
     */
    @Query(value = """
        WITH date_range AS (
            SELECT DATE(CURRENT_TIMESTAMP AT TIME ZONE 'UTC') - INTERVAL '1 day' * i AS metric_date
            FROM generate_series(0, ?1 - 1) AS i
        ),
        mine AS (
            SELECT t.* FROM tickets t
            WHERE EXISTS (SELECT 1 FROM ticket_claims tc WHERE tc.ticket_id = t.id AND tc.agent_id = CAST(?2 AS text))
        ),
        daily_metrics AS (
            SELECT
                dr.metric_date AS metric_date,
                COUNT(CASE WHEN DATE(t.created_at AT TIME ZONE 'UTC') = dr.metric_date THEN 1 END) AS created_count,
                COUNT(CASE WHEN DATE(t.resolved_at AT TIME ZONE 'UTC') = dr.metric_date THEN 1 END) AS resolved_count,
                COUNT(CASE WHEN DATE(t.closed_at AT TIME ZONE 'UTC') = dr.metric_date THEN 1 END) AS closed_count,
                COUNT(CASE WHEN DATE(t.created_at AT TIME ZONE 'UTC') = dr.metric_date AND t.sla_breached = true THEN 1 END) AS sla_breach_count
            FROM date_range dr
            LEFT JOIN mine t ON
                (DATE(t.created_at AT TIME ZONE 'UTC') = dr.metric_date OR
                 DATE(t.resolved_at AT TIME ZONE 'UTC') = dr.metric_date OR
                 DATE(t.closed_at AT TIME ZONE 'UTC') = dr.metric_date)
            GROUP BY dr.metric_date
        )
        SELECT metric_date, created_count::BIGINT, resolved_count::BIGINT, closed_count::BIGINT, sla_breach_count::BIGINT
        FROM daily_metrics
        ORDER BY metric_date DESC
        """, nativeQuery = true)
    List<Object[]> getAgentTicketTimelineMetrics(int days, String agentId);

    /** Ajanın en son claim'lediği biletler (en yeni önce). Limit {@link Pageable} ile verilir. */
    @Query(value = "SELECT t.* FROM tickets t "
         + "JOIN ticket_claims tc ON tc.ticket_id = t.id "
         + "WHERE tc.agent_id = CAST(:agentId AS text) "
         + "ORDER BY t.created_at DESC", nativeQuery = true)
    List<Ticket> findRecentClaimedByAgent(@Param("agentId") String agentId, Pageable pageable);
}
