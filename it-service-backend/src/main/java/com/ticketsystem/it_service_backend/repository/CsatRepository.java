package com.ticketsystem.it_service_backend.repository;

import com.ticketsystem.it_service_backend.entity.Csat;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface CsatRepository extends JpaRepository<Csat, Long> {
    
    boolean existsByTicketId(Long ticketId);
    
    Optional<Csat> findByTicketId(Long ticketId);
}
