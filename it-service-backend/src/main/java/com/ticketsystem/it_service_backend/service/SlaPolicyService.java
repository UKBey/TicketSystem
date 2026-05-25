package com.ticketsystem.it_service_backend.service;

import com.ticketsystem.it_service_backend.config.CacheConfig;
import com.ticketsystem.it_service_backend.config.SlaProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;

/**
 * Önceliklere göre SLA hedef sürelerini ve uyarı eşiklerini sunar.
 *
 * <p>Değerler {@code app.sla.policies} yapılandırmasından (env-driven) okunur;
 * eksik priority için tehlikesiz dahili default'lara düşer. SLA politikası
 * değişirse dashboard cache'lerini boşaltmak için
 * {@link #evictSlaDependentCaches} sağlanır.
 */
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

    /**
     * Verilen priority için SLA hedef süresini milisaniye olarak döner.
     *
     * @param priority {@code CRITICAL/HIGH/MEDIUM/LOW} (case-insensitive); null/eşleşmeyen
     *                 değer MEDIUM default'u olarak ele alınır
     * @return SLA süresi (ms)
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
     * SLA hedef süresini saat cinsinden döner (metrik aggregation'larında kullanılır).
     *
     * @param priority priority kodu
     * @return saat cinsinden SLA süresi
     */
    public int getResolutionHours(String priority) {
        return (int) (getSlaDurationMs(priority) / 3_600_000L);
    }

    /**
     * Bildirim/uyarı (upcoming-breach) tetikleme eşiğini saat cinsinden döner.
     * Konfigürasyon eksikse 2 saat default'u uygulanır.
     *
     * @param priority priority kodu
     * @return uyarı eşiği (saat)
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
