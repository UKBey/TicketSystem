package com.ticketsystem.it_service_backend.repository;

import com.ticketsystem.it_service_backend.entity.Ticket;
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

    // Belirtilen statü listesindeki ticket'ları döner.
    List<Ticket> findByStatusIn(List<String> statuses);

    // Tum ticket durumlarinin dagilimini doner.
    @Query("SELECT t.status, COUNT(t) FROM Ticket t GROUP BY t.status")
    List<Object[]> countTicketsGroupedByStatus();

    // SLA'yı aşmış açık biletler (slaBreached=true, henüz kapatılmamış).
    @Query("SELECT t FROM Ticket t WHERE t.slaBreached = true AND t.status IN ('NEW', 'IN_PROGRESS', 'WAITING_FOR_CUSTOMER') ORDER BY t.slaDeadline ASC")
    List<Ticket> findBreachedOpenTickets();

    // SLA deadline'ı [now, cutoff] aralığında olan açık biletler (yaklaşan breach).
    @Query("SELECT t FROM Ticket t WHERE t.slaBreached = false AND t.slaDeadline IS NOT NULL AND t.slaDeadline >= :now AND t.slaDeadline <= :cutoff AND t.status IN ('NEW', 'IN_PROGRESS', 'WAITING_FOR_CUSTOMER') ORDER BY t.slaDeadline ASC")
    List<Ticket> findUpcomingBreachTickets(@Param("now") ZonedDateTime now, @Param("cutoff") ZonedDateTime cutoff);

    // WAITING_FOR_CUSTOMER statüsünde cutoff tarihinden önce oluşturulmuş biletler.
    @Query("SELECT t FROM Ticket t WHERE t.status = 'WAITING_FOR_CUSTOMER' AND t.createdAt < :cutoff ORDER BY t.createdAt ASC")
    List<Ticket> findWaitingTooLongTickets(@Param("cutoff") ZonedDateTime cutoff);

    // Dönem içinde oluşturulan bilet sayısı.
    @Query("SELECT COUNT(t) FROM Ticket t WHERE t.createdAt >= :since")
    long countCreatedSince(@Param("since") ZonedDateTime since);

    // Dönem içinde çözülen bilet sayısı.
    @Query("SELECT COUNT(t) FROM Ticket t WHERE t.status = 'RESOLVED' AND t.resolvedAt >= :since")
    long countResolvedSince(@Param("since") ZonedDateTime since);

    // Dönem içinde kapatılan bilet sayısı.
    @Query("SELECT COUNT(t) FROM Ticket t WHERE t.status = 'CLOSED' AND t.closedAt >= :since")
    long countClosedSince(@Param("since") ZonedDateTime since);

    // Çözülen biletlerin ortalama çözüm süresi (saat).
    @Query("SELECT AVG(FUNCTION('TIMESTAMPDIFF', HOUR, t.createdAt, t.resolvedAt)) " +
           "FROM Ticket t WHERE t.status = 'RESOLVED' AND t.resolvedAt >= :since AND t.createdAt IS NOT NULL")
    Double avgResolutionHoursSince(@Param("since") ZonedDateTime since);

    // Çözülen biletlerde SLA ihlali yaşanmayanların oranı (%).
    @Query("SELECT " +
           "CASE WHEN COUNT(t) = 0 THEN 100.0 " +
           "ELSE (SUM(CASE WHEN t.slaBreached = false THEN 1 ELSE 0 END) * 100.0 / COUNT(t)) END " +
           "FROM Ticket t WHERE t.status = 'RESOLVED' AND t.resolvedAt >= :since")
    Double slaComplianceRateSince(@Param("since") ZonedDateTime since);

    // Son N gün içinde günlük ticket metrikleri (created, resolved, closed, sla breach) dönülür
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
