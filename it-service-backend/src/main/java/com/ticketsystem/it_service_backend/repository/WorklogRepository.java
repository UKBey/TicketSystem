package com.ticketsystem.it_service_backend.repository;

import com.ticketsystem.it_service_backend.entity.TicketWorklog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.ZonedDateTime;
import java.util.List;

/**
 * JPA repository for {@link TicketWorklog} — per-ticket / per-agent listing and a
 * per-agent minute total for the dashboard.
 */
public interface WorklogRepository extends JpaRepository<TicketWorklog, Long> {

    List<TicketWorklog> findByTicketId(Long ticketId);

    List<TicketWorklog> findByAgentId(String agentId);

    void deleteByTicketId(Long ticketId);

    /**
     * Total minutes and worklog count per agent since the given date.
     * Returns: each row is {@code [agent_id, total_minutes, worklog_count]}, with
     * the highest-effort agent at the top.
     */
    @Query("SELECT w.agentId, SUM(w.minutes), COUNT(w) " +
           "FROM TicketWorklog w " +
           "WHERE w.createdAt >= :since " +
           "GROUP BY w.agentId " +
           "ORDER BY SUM(w.minutes) DESC")
    List<Object[]> findAgentWorklogSummary(@Param("since") ZonedDateTime since);

    /**
     * Product-scoped variant of {@link #findAgentWorklogSummary}. Joins worklogs to
     * their ticket so the filter applies on the ticket's product. When
     * {@code filterByProduct} is false the result matches the global query.
     * Returns: each row is {@code [agent_id, total_minutes, worklog_count]}.
     */
    @Query(value = "SELECT w.agent_id, SUM(w.minutes), COUNT(w.id) " +
           "FROM ticket_worklogs w " +
           "JOIN tickets t ON t.id = w.ticket_id " +
           "WHERE w.created_at >= :since " +
           "AND (:filterByProduct = false OR t.product_id IN (:productIds)) " +
           "GROUP BY w.agent_id " +
           "ORDER BY SUM(w.minutes) DESC", nativeQuery = true)
    List<Object[]> findAgentWorklogSummaryScoped(@Param("since") ZonedDateTime since,
                                                 @Param("filterByProduct") boolean filterByProduct,
                                                 @Param("productIds") List<Long> productIds);

    /** Total worklog minutes a single agent logged since the given date (0 when none). */
    @Query("SELECT COALESCE(SUM(w.minutes), 0) FROM TicketWorklog w "
         + "WHERE w.agentId = :agentId AND w.createdAt >= :since")
    long sumAgentWorklogMinutesSince(@Param("agentId") String agentId,
                                     @Param("since") ZonedDateTime since);

    /**
     * Product-scoped variant of {@link #sumAgentWorklogMinutesSince}. Joins worklogs to
     * their ticket so the product filter applies on the ticket's product; when
     * {@code filterByProduct} is false it matches the global query.
     */
    @Query(value = "SELECT COALESCE(SUM(w.minutes), 0) FROM ticket_worklogs w "
         + "JOIN tickets t ON t.id = w.ticket_id "
         + "WHERE w.agent_id = CAST(:agentId AS text) AND w.created_at >= :since "
         + "AND (:filterByProduct = false OR t.product_id IN (:productIds))", nativeQuery = true)
    long sumAgentWorklogMinutesSinceScoped(@Param("agentId") String agentId,
                                           @Param("since") ZonedDateTime since,
                                           @Param("filterByProduct") boolean filterByProduct,
                                           @Param("productIds") List<Long> productIds);

    /**
     * Per-day worklog minutes for a single agent over the last {@code :days} days.
     * Gap-filled via {@code generate_series} so every day in the window is present
     * (0 when nothing was logged). Returns: each row is {@code [date, total_minutes]},
     * oldest day last. Product-scoped like the other agent queries.
     */
    @Query(value = """
            WITH date_range AS (
                SELECT DATE(CURRENT_TIMESTAMP AT TIME ZONE 'UTC') - INTERVAL '1 day' * i AS metric_date
                FROM generate_series(0, :days - 1) AS i
            ),
            mine AS (
                SELECT w.minutes, w.created_at FROM ticket_worklogs w
                JOIN tickets t ON t.id = w.ticket_id
                WHERE w.agent_id = CAST(:agentId AS text)
                AND (:filterByProduct = false OR t.product_id IN (:productIds))
            )
            SELECT dr.metric_date AS metric_date,
                COALESCE(SUM(CASE WHEN DATE(m.created_at AT TIME ZONE 'UTC') = dr.metric_date THEN m.minutes END), 0)::BIGINT AS minutes
            FROM date_range dr
            LEFT JOIN mine m ON DATE(m.created_at AT TIME ZONE 'UTC') = dr.metric_date
            GROUP BY dr.metric_date
            ORDER BY dr.metric_date DESC
            """, nativeQuery = true)
    List<Object[]> findAgentWorklogByDayScoped(@Param("agentId") String agentId,
                                               @Param("days") int days,
                                               @Param("filterByProduct") boolean filterByProduct,
                                               @Param("productIds") List<Long> productIds);
}
