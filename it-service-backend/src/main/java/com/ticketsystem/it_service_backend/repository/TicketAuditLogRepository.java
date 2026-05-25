package com.ticketsystem.it_service_backend.repository;

import com.ticketsystem.it_service_backend.entity.TicketAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

/**
 * JPA repository for {@link TicketAuditLog} — lists per-ticket audit log entries
 * newest-first; can also be filtered by action_type.
 */
public interface TicketAuditLogRepository extends JpaRepository<TicketAuditLog, Long> {

    List<TicketAuditLog> findByTicketIdOrderByCreatedAtDesc(Long ticketId);

    List<TicketAuditLog> findByTicketIdAndActionTypeOrderByCreatedAtDesc(Long ticketId, String actionType);
}
