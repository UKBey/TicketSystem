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
}
