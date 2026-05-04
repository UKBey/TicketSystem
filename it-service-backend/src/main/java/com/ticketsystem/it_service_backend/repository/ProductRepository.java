package com.ticketsystem.it_service_backend.repository;

import com.ticketsystem.it_service_backend.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;

public interface ProductRepository extends JpaRepository<Product, Long> {

    // Ürün bazında toplam, açık, ortalama çözüm, CSAT ve SLA breach metriklerini döner
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
            LEFT JOIN csat_surveys cs ON cs.ticket_id = t.id
            WHERE p.is_active = true
            GROUP BY p.id, p.name
            ORDER BY total_tickets DESC
            """, nativeQuery = true)
    List<Object[]> findProductMetrics();
}

