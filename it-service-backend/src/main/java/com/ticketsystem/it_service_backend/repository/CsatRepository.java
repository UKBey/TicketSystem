package com.ticketsystem.it_service_backend.repository;

import com.ticketsystem.it_service_backend.entity.Csat;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

/**
 * JPA repository for {@link Csat} — on top of standard CRUD, exposes aggregate
 * queries (average rating, distribution, per-priority breakdown) for dashboard KPIs.
 */
public interface CsatRepository extends JpaRepository<Csat, Long> {

    boolean existsByTicketId(Long ticketId);

    Optional<Csat> findByTicketId(Long ticketId);

    void deleteByTicketId(Long ticketId);

    /** Returns the all-time average CSAT rating for the dashboard KPI. */
    @Query("SELECT AVG(CAST(c.rating AS double)) FROM Csat c")
    Double findAverageRating();

    /** Returns the average CSAT rating from the given date onwards. */
    @Query("SELECT AVG(CAST(c.rating AS double)) FROM Csat c WHERE c.createdAt >= :since")
    Double findAverageRatingSince(@Param("since") ZonedDateTime since);

    /** Response distribution by rating from the given date onwards: each row is {@code [rating, count]}. */
    @Query("SELECT c.rating, COUNT(c) FROM Csat c WHERE c.createdAt >= :since GROUP BY c.rating ORDER BY c.rating")
    List<Object[]> findRatingDistributionSince(@Param("since") ZonedDateTime since);

    /**
     * Average CSAT rating broken down by ticket priority — native join against
     * {@code tickets}. Returns: each row is {@code [priority, avg_rating, count]}.
     */
    @Query(value = """
            SELECT t.priority, AVG(CAST(c.rating AS FLOAT)), COUNT(c.id)
            FROM csat_surveys c
            JOIN tickets t ON t.id = c.ticket_id
            WHERE c.created_at >= :since
            GROUP BY t.priority
            """, nativeQuery = true)
    List<Object[]> findAverageRatingByPrioritySince(@Param("since") ZonedDateTime since);

    /**
     * Comment text from recent CSATs with a high rating (≥4) and a meaningful
     * comment (5+ characters). The limit is applied via {@link Pageable} — used by
     * the "voice of the customer" panel on the landing/dashboard.
     */
    @Query("SELECT c.comment FROM Csat c WHERE c.comment IS NOT NULL AND LENGTH(c.comment) > 5 AND c.rating >= 4 AND c.createdAt >= :since ORDER BY c.createdAt DESC")
    List<String> findTopPositiveCommentsSince(@Param("since") ZonedDateTime since, Pageable pageable);
}
