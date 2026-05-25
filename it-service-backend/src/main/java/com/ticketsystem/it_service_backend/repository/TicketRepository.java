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
 * for role-based (customer / agent / agent_admin) filtered paged listings,
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
     * All active tickets (for the AGENT_ADMIN panel). The old code called
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

    /** Pool (NEW) tickets — AGENT_ADMIN, all products + all filters. */
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

    /** Team tickets — AGENT_ADMIN, all products + all filters. */
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

    /** Team tickets — pages active tickets (excluding NEW/CLOSED) across all products for AGENT_ADMIN. */
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

    // Havuz (NEW) biletleri — AGENT_ADMIN icin tum urunler, SLA urgency sirasi ile
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

    // Takim biletleri — AGENT_ADMIN icin tum urunler, SLA urgency sirasi ile
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

    /** Total ticket count for the given statuses (typically the "open" status list). */
    @Query("SELECT COUNT(t) FROM Ticket t WHERE t.status IN :statuses")
    Long countByStatusIn(@Param("statuses") List<String> statuses);

    /** Count of SLA-breached tickets among those in the given statuses. */
    @Query("SELECT COUNT(t) FROM Ticket t WHERE t.status IN :statuses AND t.slaBreached = true")
    Long countSlaBreachedByStatusIn(@Param("statuses") List<String> statuses);

    /** Count of tickets created since the given date that match the status filter (for KPIs). */
    @Query("SELECT COUNT(t) FROM Ticket t WHERE t.status IN :statuses AND t.createdAt >= :since")
    Long countCreatedSinceByStatusIn(@Param("statuses") List<String> statuses,
                                      @Param("since") java.time.ZonedDateTime since);

    /** Priority-based distribution of tickets in the given statuses: each row is {@code [priority, count]}. */
    @Query("SELECT t.priority, COUNT(t) FROM Ticket t WHERE t.status IN :statuses GROUP BY t.priority")
    List<Object[]> countByStatusInGroupByPriority(@Param("statuses") List<String> statuses);

    /** Average resolution time for RESOLVED tickets (hours) — all time. */
    @Query(value = "SELECT AVG(EXTRACT(EPOCH FROM (t.resolved_at - t.created_at)) / 3600.0) FROM tickets t WHERE t.status = 'RESOLVED' AND t.created_at IS NOT NULL AND t.resolved_at IS NOT NULL", nativeQuery = true)
    Double findAvgResolutionHoursForResolved();

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

    /** Open + SLA-breached tickets, sorted ascending by deadline (for the alert page and the scheduler). */
    @Query("SELECT t FROM Ticket t WHERE t.status IN :statuses AND t.slaBreached = true ORDER BY t.slaDeadline ASC")
    List<Ticket> findBreachedOpenTickets(@Param("statuses") List<String> statuses, Pageable pageable);

    /** Tickets with a deadline before {@code before} that have not yet breached and are not paused. */
    @Query("SELECT t FROM Ticket t WHERE t.status IN :statuses AND t.slaBreached = false AND t.slaPausedAt IS NULL AND t.slaDeadline IS NOT NULL AND t.slaDeadline <= :before ORDER BY t.slaDeadline ASC")
    List<Ticket> findUpcomingBreachTickets(@Param("statuses") List<String> statuses, @Param("before") ZonedDateTime before, Pageable pageable);

    /** Variant of {@link #findUpcomingBreachTickets} narrowed by priority filter (for the critical-priority scheduler). */
    @Query("SELECT t FROM Ticket t WHERE t.status IN :statuses AND t.priority IN :priorities AND t.slaBreached = false AND t.slaPausedAt IS NULL AND t.slaDeadline IS NOT NULL AND t.slaDeadline <= :before ORDER BY t.slaDeadline ASC")
    List<Ticket> findUpcomingBreachTicketsByPriority(@Param("statuses") List<String> statuses, @Param("priorities") List<String> priorities, @Param("before") ZonedDateTime before, Pageable pageable);

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

    /** Count of tickets with no claims — within the given status filter (typically NEW). */
    @Query("SELECT COUNT(t) FROM Ticket t WHERE t.status IN :statuses AND NOT EXISTS (SELECT 1 FROM TicketClaim tc WHERE tc.ticket = t)")
    long countUnassignedByStatusIn(@Param("statuses") List<String> statuses);

    long countByStatus(String status);

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
}
