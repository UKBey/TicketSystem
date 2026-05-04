package com.ticketsystem.it_service_backend.repository;

import com.ticketsystem.it_service_backend.entity.Ticket;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;
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
