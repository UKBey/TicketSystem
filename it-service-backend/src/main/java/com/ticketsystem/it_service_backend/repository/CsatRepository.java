package com.ticketsystem.it_service_backend.repository;

import com.ticketsystem.it_service_backend.entity.Csat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.Optional;

public interface CsatRepository extends JpaRepository<Csat, Long> {
    
    boolean existsByTicketId(Long ticketId);
    
    Optional<Csat> findByTicketId(Long ticketId);
    void deleteByTicketId(Long ticketId);

    // Dashboard metrikleri için CSAT puan ortalaması
    @Query("SELECT AVG(CAST(c.rating AS double)) FROM Csat c")
    Double findAverageRating();
}
