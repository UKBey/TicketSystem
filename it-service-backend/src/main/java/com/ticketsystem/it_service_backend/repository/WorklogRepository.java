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
}
