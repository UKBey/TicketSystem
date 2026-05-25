package com.ticketsystem.it_service_backend.repository;

import com.ticketsystem.it_service_backend.entity.TicketWorklog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.ZonedDateTime;
import java.util.List;

/**
 * {@link TicketWorklog} için JPA repository — bilet/agent bazında listeleme ve
 * dashboard için agent başına dakika toplamı.
 */
public interface WorklogRepository extends JpaRepository<TicketWorklog, Long> {

    List<TicketWorklog> findByTicketId(Long ticketId);

    List<TicketWorklog> findByAgentId(String agentId);

    void deleteByTicketId(Long ticketId);

    /**
     * Verilen tarihten beri agent başına toplam dakika ve worklog kayıt sayısı.
     * Dönüş: her satır {@code [agent_id, total_minutes, worklog_count]}, en çok eforu
     * harcayan en üstte.
     */
    @Query("SELECT w.agentId, SUM(w.minutes), COUNT(w) " +
           "FROM TicketWorklog w " +
           "WHERE w.createdAt >= :since " +
           "GROUP BY w.agentId " +
           "ORDER BY SUM(w.minutes) DESC")
    List<Object[]> findAgentWorklogSummary(@Param("since") ZonedDateTime since);
}
