package com.ticketsystem.it_service_backend.repository;

import com.ticketsystem.it_service_backend.entity.Ticket;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;
import java.time.ZonedDateTime;
import java.util.List;

public interface TicketRepository extends JpaRepository<Ticket, Long> {

    // Musterinin olusturdugu biletleri listeler.
    List<Ticket> findByCustomerId(String customerId);

    // Agentin uzerine atanmis biletleri listeler.
    List<Ticket> findByAssigneeId(String assigneeId);

    // Havuzdaki NEW ve henuz sahiplenilmemis kayitlari getirir.
    List<Ticket> findByStatus(String status);
    
    // Agentin yetkili oldugu urunlere ait NEW biletleri getirir.
    List<Ticket> findByStatusAndProductIdIn(String status, List<Long> productIds);

    // Belirtilen urun listesine ait tum biletleri statuden bagimsiz dondurur.
    List<Ticket> findByProductIdIn(List<Long> productIds);

    // Karma rolde kullanicinin hem sahip oldugu hem yetkili oldugu urun biletlerini birlestirir.
    List<Ticket> findByCustomerIdOrProductIdIn(String customerId, List<Long> productIds);

    // Tum ticket durumlarinin dagilimini doner.
    @Query("SELECT t.status, COUNT(t) FROM Ticket t GROUP BY t.status")
    List<Object[]> countTicketsGroupedByStatus();

    // Açık biletlerin toplam sayısı (getDashboardSummary optimizasyonu)
    @Query("SELECT COUNT(t) FROM Ticket t WHERE t.status IN :statuses")
    Long countByStatusIn(@Param("statuses") List<String> statuses);

    // Açık biletler arasında SLA ihlali yapanların sayısı
    @Query("SELECT COUNT(t) FROM Ticket t WHERE t.status IN :statuses AND t.slaBreached = true")
    Long countSlaBreachedByStatusIn(@Param("statuses") List<String> statuses);

    // Son 24 saat içinde açık biletler arasında oluşturulanların sayısı
    @Query("SELECT COUNT(t) FROM Ticket t WHERE t.status IN :statuses AND t.createdAt >= :since")
    Long countCreatedSinceByStatusIn(@Param("statuses") List<String> statuses,
                                      @Param("since") java.time.ZonedDateTime since);

    // Açık biletlerin priority dağılımı — [priority, count]
    @Query("SELECT t.priority, COUNT(t) FROM Ticket t WHERE t.status IN :statuses GROUP BY t.priority")
    List<Object[]> countByStatusInGroupByPriority(@Param("statuses") List<String> statuses);

    // RESOLVED biletlerin ortalama çözüm süresi (saat) — native: Hibernate 7 JPQL EXTRACT(EPOCH FROM interval) desteklemiyor
    @Query(value = "SELECT AVG(EXTRACT(EPOCH FROM (t.resolved_at - t.created_at)) / 3600.0) FROM tickets t WHERE t.status = 'RESOLVED' AND t.created_at IS NOT NULL AND t.resolved_at IS NOT NULL", nativeQuery = true)
    Double findAvgResolutionHoursForResolved();

    // Worklog completion — dönem bilet sayımları
    @Query("SELECT COUNT(t) FROM Ticket t WHERE t.createdAt >= :since")
    long countCreatedSince(@Param("since") ZonedDateTime since);

    @Query("SELECT COUNT(t) FROM Ticket t WHERE t.status = 'RESOLVED' AND t.resolvedAt >= :since")
    long countResolvedSince(@Param("since") ZonedDateTime since);

    @Query("SELECT COUNT(t) FROM Ticket t WHERE t.status = 'CLOSED' AND t.closedAt >= :since")
    long countClosedSince(@Param("since") ZonedDateTime since);

    @Query(value = "SELECT AVG(EXTRACT(EPOCH FROM (t.resolved_at - t.created_at)) / 3600.0) FROM tickets t WHERE t.status = 'RESOLVED' AND t.resolved_at >= :since AND t.created_at IS NOT NULL AND t.resolved_at IS NOT NULL", nativeQuery = true)
    Double avgResolutionHoursSince(@Param("since") ZonedDateTime since);

    @Query(value = "SELECT (COUNT(CASE WHEN t.sla_breached = false THEN 1 END) * 100.0) / NULLIF(COUNT(t.id), 0) FROM tickets t WHERE t.status = 'RESOLVED' AND t.resolved_at >= :since", nativeQuery = true)
    Double slaComplianceRateSince(@Param("since") ZonedDateTime since);

    // Alert sorguları
    @Query("SELECT t FROM Ticket t WHERE t.status IN :statuses AND t.slaBreached = true ORDER BY t.slaDeadline ASC")
    List<Ticket> findBreachedOpenTickets(@Param("statuses") List<String> statuses, Pageable pageable);

    @Query("SELECT t FROM Ticket t WHERE t.status IN :statuses AND t.slaBreached = false AND t.slaDeadline IS NOT NULL AND t.slaDeadline <= :before ORDER BY t.slaDeadline ASC")
    List<Ticket> findUpcomingBreachTickets(@Param("statuses") List<String> statuses, @Param("before") ZonedDateTime before, Pageable pageable);

    @Query("SELECT t FROM Ticket t WHERE t.status = 'WAITING_FOR_CUSTOMER' AND t.createdAt <= :since ORDER BY t.createdAt ASC")
    List<Ticket> findWaitingTooLongTickets(@Param("since") ZonedDateTime since, Pageable pageable);

    @Query("SELECT COUNT(t) FROM Ticket t WHERE t.status IN :statuses AND t.assigneeId IS NULL")
    long countUnassignedByStatusIn(@Param("statuses") List<String> statuses);

    long countByStatus(String status);

    @Query(value = "SELECT AVG(EXTRACT(EPOCH FROM (CURRENT_TIMESTAMP - t.created_at)) / 3600.0) FROM tickets t WHERE t.status IN (:statuses) AND t.created_at IS NOT NULL", nativeQuery = true)
    Double avgWaitingHoursForOpen(@Param("statuses") List<String> statuses);

    @Query(value = """
        WITH date_range AS (
            SELECT DATE(CURRENT_TIMESTAMP AT TIME ZONE 'UTC') - INTERVAL '1 day' * i AS metric_date
            FROM generate_series(0, ?1 - 1) AS i
        ),
        daily_metrics AS (
            SELECT
                COALESCE(DATE(t.created_at AT TIME ZONE 'UTC'), dr.metric_date) AS metric_date,
                COUNT(CASE WHEN DATE(t.created_at AT TIME ZONE 'UTC') = dr.metric_date THEN 1 END) AS created_count,
                COUNT(CASE WHEN DATE(t.resolved_at AT TIME ZONE 'UTC') = dr.metric_date THEN 1 END) AS resolved_count,
                COUNT(CASE WHEN DATE(t.closed_at AT TIME ZONE 'UTC') = dr.metric_date THEN 1 END) AS closed_count,
                COUNT(CASE WHEN DATE(t.created_at AT TIME ZONE 'UTC') = dr.metric_date AND t.sla_breached = true THEN 1 END) AS sla_breach_count
            FROM date_range dr
            LEFT JOIN tickets t ON 
                DATE(t.created_at AT TIME ZONE 'UTC') = dr.metric_date OR
                DATE(t.resolved_at AT TIME ZONE 'UTC') = dr.metric_date OR
                DATE(t.closed_at AT TIME ZONE 'UTC') = dr.metric_date
            GROUP BY dr.metric_date, COALESCE(DATE(t.created_at AT TIME ZONE 'UTC'), dr.metric_date)
        )
        SELECT 
            metric_date,
            SUM(created_count)::BIGINT,
            SUM(resolved_count)::BIGINT,
            SUM(closed_count)::BIGINT,
            SUM(sla_breach_count)::BIGINT
        FROM daily_metrics
        GROUP BY metric_date
        ORDER BY metric_date DESC
        """, nativeQuery = true)
    List<Object[]> getTicketTimelineMetrics(int days);
}
