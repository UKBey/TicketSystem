package com.ticketsystem.it_service_backend.repository;

import com.ticketsystem.it_service_backend.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

/**
 * JPA repository for {@link Product} — on top of standard CRUD, aggregates
 * per-product ticket / CSAT / SLA metrics for the dashboard in a single native query.
 */
public interface ProductRepository extends JpaRepository<Product, Long> {

    /**
     * Returns, for each active product, the total/open ticket counts, average
     * resolution hours, CSAT average, SLA breach count and breach percentage in a
     * single round-trip. {@code days} null or 0 means all time; otherwise it limits
     * to tickets created within the last N days.
     * Returned columns, in order: id, name, total_tickets, open_tickets,
     * avg_resolution_hours, csat_average, sla_breach_count, sla_breach_percentage.
     */
    @Query(value = """
            SELECT
                p.id,
                p.name,
                COUNT(t.id)                                                         AS total_tickets,
                COUNT(CASE WHEN t.status IN ('NEW','IN_PROGRESS','WAITING_FOR_CUSTOMER') THEN 1 END) AS open_tickets,
                AVG(CASE WHEN t.resolved_at IS NOT NULL
                    THEN EXTRACT(EPOCH FROM (t.resolved_at - t.created_at)) / 3600.0 END) AS avg_resolution_hours,
                AVG(CAST(cs.rating AS DOUBLE PRECISION))                            AS csat_average,
                COUNT(CASE WHEN t.sla_breached = true THEN 1 END)                  AS sla_breach_count,
                CASE WHEN COUNT(t.id) > 0
                    THEN ROUND(COUNT(CASE WHEN t.sla_breached = true THEN 1 END) * 100.0 / COUNT(t.id), 2)
                    ELSE 0
                END                                                                 AS sla_breach_percentage
            FROM products p
            LEFT JOIN tickets t ON t.product_id = p.id
                AND (CAST(:days AS INTEGER) IS NULL
                     OR t.created_at >= NOW() - make_interval(days => CAST(:days AS INTEGER)))
            LEFT JOIN csat_surveys cs ON cs.ticket_id = t.id
            WHERE p.is_active = true
            GROUP BY p.id, p.name
            ORDER BY total_tickets DESC
            """, nativeQuery = true)
    List<Object[]> findProductMetrics(@Param("days") Integer days);
}
