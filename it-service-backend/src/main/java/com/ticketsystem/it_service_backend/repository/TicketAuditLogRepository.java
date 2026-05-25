package com.ticketsystem.it_service_backend.repository;

import com.ticketsystem.it_service_backend.entity.TicketAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

/**
 * {@link TicketAuditLog} için JPA repository — bilet bazlı denetim kayıtlarını
 * en yeni → en eski sırada listeler; action_type filtresiyle de sorgulanabilir.
 */
public interface TicketAuditLogRepository extends JpaRepository<TicketAuditLog, Long> {

    List<TicketAuditLog> findByTicketIdOrderByCreatedAtDesc(Long ticketId);

    List<TicketAuditLog> findByTicketIdAndActionTypeOrderByCreatedAtDesc(Long ticketId, String actionType);
}
