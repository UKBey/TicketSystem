package com.ticketsystem.llmservice.repository;

import com.ticketsystem.llmservice.entity.TicketAiSummary;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * {@link TicketAiSummary} kayıtları için JPA repository.
 *
 * <p>Bir ticket'a ait özet geçmişine erişim sağlar — özetler silinmez, her
 * üretim yeni bir satır oluşturur.
 */
@Repository
public interface TicketAiSummaryRepository extends JpaRepository<TicketAiSummary, Long> {

    /** Bir ticket'ın tüm özetlerini en yeniden eskiye sıralar */
    List<TicketAiSummary> findByTicketIdOrderByCreatedAtDesc(Long ticketId);

    /** Bir ticket'ın en son özetini döner */
    Optional<TicketAiSummary> findFirstByTicketIdOrderByCreatedAtDesc(Long ticketId);
}
