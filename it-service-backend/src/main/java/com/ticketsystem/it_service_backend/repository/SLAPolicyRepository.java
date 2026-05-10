package com.ticketsystem.it_service_backend.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class SLAPolicyRepository {

    private final JdbcTemplate jdbcTemplate;

    public List<Object[]> findPrioritySlaMetrics() {
        String sql = """
                WITH priorities AS (
                    SELECT *
                    FROM (
                        VALUES
                            ('CRITICAL', 4),
                            ('HIGH', 8),
                            ('MEDIUM', 16),
                            ('LOW', 48)
                    ) AS p(priority, default_target_hours)
                ),
                policy_target AS (
                    SELECT
                        p.priority,
                        COALESCE(sp.target_resolution_hours, p.default_target_hours) AS target_hours
                    FROM priorities p
                    LEFT JOIN sla_policies sp ON sp.priority = p.priority
                ),
                ticket_base AS (
                    SELECT
                        pt.priority,
                        pt.target_hours,
                        t.id,
                        t.created_at,
                        t.resolved_at,
                        COALESCE(t.sla_breached, false) AS sla_breached
                    FROM policy_target pt
                    LEFT JOIN tickets t ON t.priority = pt.priority
                )
                SELECT
                    tb.priority,
                    COUNT(tb.id)::BIGINT AS ticket_count,
                    tb.target_hours::INTEGER AS sla_target_hours,
                    COALESCE(
                        AVG(
                            CASE
                                WHEN tb.created_at IS NOT NULL AND tb.resolved_at IS NOT NULL
                                    THEN EXTRACT(EPOCH FROM (tb.resolved_at - tb.created_at)) / 3600.0
                            END
                        ),
                        0
                    )::DOUBLE PRECISION AS avg_resolution_hours,
                    COUNT(CASE WHEN tb.sla_breached = true THEN 1 END)::BIGINT AS breach_count,
                    CASE
                        WHEN COUNT(tb.id) = 0 THEN 0::DOUBLE PRECISION
                        ELSE ROUND((COUNT(CASE WHEN tb.sla_breached = true THEN 1 END)::numeric * 100.0) / COUNT(tb.id), 2)::DOUBLE PRECISION
                    END AS breach_percentage,
                    CASE
                        WHEN COUNT(tb.id) = 0 THEN 100::DOUBLE PRECISION
                        ELSE ROUND((COUNT(CASE WHEN tb.sla_breached = false THEN 1 END)::numeric * 100.0) / COUNT(tb.id), 2)::DOUBLE PRECISION
                    END AS on_time_percentage
                FROM ticket_base tb
                GROUP BY tb.priority, tb.target_hours
                ORDER BY CASE tb.priority
                    WHEN 'CRITICAL' THEN 1
                    WHEN 'HIGH' THEN 2
                    WHEN 'MEDIUM' THEN 3
                    WHEN 'LOW' THEN 4
                    ELSE 5
                END
                """;

        return jdbcTemplate.query(sql, (rs, rowNum) -> new Object[]{
                rs.getString("priority"),
                rs.getLong("ticket_count"),
                rs.getInt("sla_target_hours"),
                rs.getDouble("avg_resolution_hours"),
                rs.getLong("breach_count"),
                rs.getDouble("breach_percentage"),
                rs.getDouble("on_time_percentage")
        });
    }
}
