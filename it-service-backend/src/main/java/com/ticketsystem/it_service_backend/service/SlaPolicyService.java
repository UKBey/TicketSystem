package com.ticketsystem.it_service_backend.service;

import com.ticketsystem.it_service_backend.config.CacheConfig;
import com.ticketsystem.it_service_backend.config.SlaProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;

/**
 * Exposes SLA target durations and warning thresholds per priority.
 *
 * <p>Values are read from the {@code app.sla.policies} configuration (env-driven)
 * and fall back to safe in-code defaults when a priority is missing.
 * {@link #evictSlaDependentCaches} is provided to flush dashboard caches when
 * the SLA policy changes.
 */
@Service
@RequiredArgsConstructor
public class SlaPolicyService {

    private final SlaProperties slaProperties;

    /**
     * Flushes all dashboard caches that depend on SLA policy when it changes.
     * The SLA policy is currently env-driven (no DB-backed admin endpoint), but
     * this is the single entry point to call if such a flow is ever added or if
     * a manual ops flush is needed. Runtime flushes can also be triggered via
     * DELETE on `/actuator/caches/{name}` (admin role required by SecurityConfig).
     */
    @Caching(evict = {
            @CacheEvict(value = CacheConfig.PRIORITY_SLA_METRICS, allEntries = true),
            @CacheEvict(value = CacheConfig.DASHBOARD_SUMMARY,    allEntries = true),
            @CacheEvict(value = CacheConfig.AGENT_PERFORMANCE,    allEntries = true)
    })
    public void evictSlaDependentCaches() {
        // Method body bos; @CacheEvict aspect'i flush'i yapar.
    }

    /**
     * Returns the SLA target duration for the given priority in milliseconds.
     *
     * @param priority {@code CRITICAL/HIGH/MEDIUM/LOW} (case-insensitive); null or
     *                 unrecognized values fall back to the MEDIUM default
     * @return SLA duration in ms
     */
    public long getSlaDurationMs(String priority) {
        if (priority == null) return defaultMs("MEDIUM");
        SlaProperties.PolicyConfig cfg = slaProperties.getPolicies().get(priority.toUpperCase());
        if (cfg != null && cfg.getResolutionHours() > 0) {
            return (long) cfg.getResolutionHours() * 3_600_000L;
        }
        return defaultMs(priority.toUpperCase());
    }

    /**
     * Returns the SLA target duration in hours (used by metric aggregations).
     *
     * @param priority priority code
     * @return SLA duration in hours
     */
    public int getResolutionHours(String priority) {
        return (int) (getSlaDurationMs(priority) / 3_600_000L);
    }

    /**
     * Returns the upcoming-breach notification/warning threshold in hours.
     * Falls back to 2 hours when no configuration is present.
     *
     * @param priority priority code
     * @return warning threshold in hours
     */
    public int getWarningThresholdHours(String priority) {
        if (priority == null) return 2;
        SlaProperties.PolicyConfig cfg = slaProperties.getPolicies().get(priority.toUpperCase());
        return cfg != null ? cfg.getWarningThresholdHours() : 2;
    }

    private long defaultMs(String priority) {
        return switch (priority) {
            case "CRITICAL" ->  1L * 3_600_000L;
            case "HIGH"     ->  4L * 3_600_000L;
            case "MEDIUM"   -> 12L * 3_600_000L;
            case "LOW"      -> 24L * 3_600_000L;
            default         -> 12L * 3_600_000L;
        };
    }
}
