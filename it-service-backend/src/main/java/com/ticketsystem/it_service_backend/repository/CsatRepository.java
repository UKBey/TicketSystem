package com.ticketsystem.it_service_backend.repository;

import com.ticketsystem.it_service_backend.entity.Csat;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

public interface CsatRepository extends JpaRepository<Csat, Long> {

    boolean existsByTicketId(Long ticketId);

    Optional<Csat> findByTicketId(Long ticketId);

    void deleteByTicketId(Long ticketId);

    // Dashboard KPI için genel CSAT ortalaması
    @Query("SELECT AVG(CAST(c.rating AS double)) FROM Csat c")
    Double findAverageRating();

    // Belirli tarihten itibaren CSAT ortalaması
    @Query("SELECT AVG(CAST(c.rating AS double)) FROM Csat c WHERE c.createdAt >= :since")
    Double findAverageRatingSince(@Param("since") ZonedDateTime since);

    // Belirli tarihten itibaren puana göre yanıt dağılımı (rating, count)
    @Query("SELECT c.rating, COUNT(c) FROM Csat c WHERE c.createdAt >= :since GROUP BY c.rating ORDER BY c.rating")
    List<Object[]> findRatingDistributionSince(@Param("since") ZonedDateTime since);

    // Priority bazlı CSAT ortalaması — tickets tablosuyla native join
    @Query(value = """
            SELECT t.priority, AVG(CAST(c.rating AS FLOAT)), COUNT(c.id)
            FROM csat_surveys c
            JOIN tickets t ON t.id = c.ticket_id
            WHERE c.created_at >= :since
            GROUP BY t.priority
            """, nativeQuery = true)
    List<Object[]> findAverageRatingByPrioritySince(@Param("since") ZonedDateTime since);

    // Yüksek puanlı (>=4) ve yorum içeren son kayıtlar
    @Query("SELECT c.comment FROM Csat c WHERE c.comment IS NOT NULL AND LENGTH(c.comment) > 5 AND c.rating >= 4 AND c.createdAt >= :since ORDER BY c.createdAt DESC")
    List<String> findTopPositiveCommentsSince(@Param("since") ZonedDateTime since, Pageable pageable);
}
