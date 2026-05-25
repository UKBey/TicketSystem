package com.ticketsystem.it_service_backend.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * Caffeine-tabanli in-process cache yapilandirmasi.
 *
 * <p>{@code @EnableCaching} ile birlikte calisir; dashboard metrik servisleri ve
 * rate-limit config servisi cache anahtarlarini buradaki sabitlerle paylasir.
 * Tum cache'ler ortak bir TTL (5 dakika {@code expireAfterWrite}) ve
 * {@code maximumSize=500} ile yonetilir. Cache invalidation icin {@code @CacheEvict}
 * (config guncellemeleri) ve {@code DELETE /actuator/caches/{name}} (manuel flush)
 * kullanilir.
 */
@Configuration
public class CacheConfig {

    // Dashboard metrikleri okuma ağırlıklı ve pahalı sorgular içerir;
    // 5 dakikalık TTL operasyonel tazeliği korurken DB baskısını azaltır.
    public static final String DASHBOARD_SUMMARY      = "dashboardSummary";
    public static final String STATUS_DISTRIBUTION    = "statusDistribution";
    public static final String AGENT_PERFORMANCE      = "agentPerformance";
    public static final String TICKET_TIMELINE        = "ticketTimeline";
    public static final String PRIORITY_SLA_METRICS   = "prioritySlaMetrics";
    public static final String PRODUCT_METRICS        = "productMetrics";
    public static final String CSAT_METRICS           = "csatMetrics";
    public static final String WORKLOG_COMPLETION     = "worklogCompletion";

    // Rate limit config'leri; interceptor her istek öncesi okur.
    // @CacheEvict admin güncellemesinde anında geçersiz kılar; TTL sadece fallback.
    public static final String RATE_LIMIT_CONFIGS     = "rateLimitConfigs";

    /**
     * Onceden tanimli isimlerle bir {@link CaffeineCacheManager} kurar.
     *
     * <p>Listede olmayan bir cache adi {@code @Cacheable} ile kullanilamaz —
     * sessizce pas gecmek yerine erken hata vermek tercih edildi. Caffeine
     * yapilandirmasi {@code expireAfterWrite=5m}, {@code maximumSize=500}.
     */
    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager(
                DASHBOARD_SUMMARY,
                STATUS_DISTRIBUTION,
                AGENT_PERFORMANCE,
                TICKET_TIMELINE,
                PRIORITY_SLA_METRICS,
                PRODUCT_METRICS,
                CSAT_METRICS,
                WORKLOG_COMPLETION,
                RATE_LIMIT_CONFIGS,
                "metrics"
        );
        manager.setCaffeine(Caffeine.newBuilder()
                .expireAfterWrite(5, TimeUnit.MINUTES)
                .maximumSize(500));
        return manager;
    }
}
