package com.ticketsystem.it_service_backend.service;

import com.ticketsystem.it_service_backend.config.CacheConfig;
import com.ticketsystem.it_service_backend.config.SlaProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SlaPolicyService {

    private final SlaProperties slaProperties;

    /**
     * SLA policy degistiginde ilgili tum dashboard cache'lerini bosaltir.
     * Su an SLA policy DB-backed bir admin endpoint'i ile guncellenmiyor (env-driven),
     * ama ileride boyle bir akis eklenirse veya manuel ops flush gerekirse cagrilacak
     * tek metod buradadir. Ayrica `/actuator/caches/{name}` DELETE ile de runtime flush
     * yapilabilir (SecurityConfig admin rol gerektirir).
     */
    @Caching(evict = {
            @CacheEvict(value = CacheConfig.PRIORITY_SLA_METRICS, allEntries = true),
            @CacheEvict(value = CacheConfig.DASHBOARD_SUMMARY,    allEntries = true),
            @CacheEvict(value = CacheConfig.AGENT_PERFORMANCE,    allEntries = true)
    })
    public void evictSlaDependentCaches() {
        // Method body bos; @CacheEvict aspect'i flush'i yapar.
    }

    public long getSlaDurationMs(String priority) {
        if (priority == null) return defaultMs("MEDIUM");
        SlaProperties.PolicyConfig cfg = slaProperties.getPolicies().get(priority.toUpperCase());
        if (cfg != null && cfg.getResolutionHours() > 0) {
            return (long) cfg.getResolutionHours() * 3_600_000L;
        }
        return defaultMs(priority.toUpperCase());
    }

    public int getResolutionHours(String priority) {
        return (int) (getSlaDurationMs(priority) / 3_600_000L);
    }

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
