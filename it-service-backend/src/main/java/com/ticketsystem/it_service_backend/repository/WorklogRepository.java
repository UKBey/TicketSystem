package com.ticketsystem.it_service_backend.repository;

import com.ticketsystem.it_service_backend.entity.TicketWorklog;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface WorklogRepository extends JpaRepository<TicketWorklog, Long> {

    List<TicketWorklog> findByTicketId(Long ticketId);

    List<TicketWorklog> findByAgentId(String agentId);
}
