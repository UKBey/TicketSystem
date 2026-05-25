package com.ticketsystem.it_service_backend.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

/**
 * Native-SQL repository that computes SLA metrics — uses {@code JdbcTemplate} and is
 * not tied to any JPA entity. Should not be confused with {@link SlaPolicyJpaRepository}:
 * that one reads policies, while this one produces the aggregate report.
 */
@Repository
@RequiredArgsConstructor
public class SLAPolicyRepository {

    private final JdbcTemplate jdbcTemplate;

    /**
     * Computes SLA metrics broken down by priority. The SLA target hours are supplied
     * by the caller (the env-driven SlaPolicyService) — the single source of truth.
     *
     * @param priorityHours map of CRITICAL/HIGH/MEDIUM/LOW → target hours
     * @param days          counting window; null or 0 means all time
     */
    public List<Object[]> findPrioritySlaMetrics(Map<String, Integer> priorityHours, Integer days) {
        Integer dayWindow = (days != null && days > 0) ? days : null;

        String sql = """
                WITH priorities AS (
                    SELECT * FROM (
                        VALUES
                            ('CRITICAL'::text, CAST(? AS INTEGER)),
                            ('HIGH'::text,     CAST(? AS INTEGER)),
                            ('MEDIUM'::text,   CAST(? AS INTEGER)),
                            ('LOW'::text,      CAST(? AS INTEGER))
                    ) AS p(priority, target_hours)
                ),
                ticket_base AS (
                    SELECT
                        p.priority,
                        p.target_hours,
                        t.id,
                        t.created_at,
                        t.resolved_at,
                        COALESCE(t.sla_breached, false) AS sla_breached
                    FROM priorities p
                    LEFT JOIN tickets t
                        ON t.priority = p.priority
                       AND (CAST(? AS INTEGER) IS NULL
                            OR t.created_at >= NOW() - make_interval(days => CAST(? AS INTEGER)))
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

        Object[] args = new Object[]{
                priorityHours.get("CRITICAL"),
                priorityHours.get("HIGH"),
                priorityHours.get("MEDIUM"),
                priorityHours.get("LOW"),
                dayWindow,
                dayWindow
        };

        return jdbcTemplate.query(sql, (rs, rowNum) -> new Object[]{
                rs.getString("priority"),
                rs.getLong("ticket_count"),
                rs.getInt("sla_target_hours"),
                rs.getDouble("avg_resolution_hours"),
                rs.getLong("breach_count"),
                rs.getDouble("breach_percentage"),
                rs.getDouble("on_time_percentage")
        }, args);
    }
}
