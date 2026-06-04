package com.ticketsystem.it_service_backend.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * Caffeine-based in-process cache configuration.
 *
 * <p>Works alongside {@code @EnableCaching}; the dashboard metrics services
 * and the rate-limit config service share cache keys via the constants
 * defined here. Every cache is governed by a common TTL
 * ({@code expireAfterWrite=5 minutes}) and {@code maximumSize=500}. Cache
 * invalidation uses {@code @CacheEvict} (on configuration updates) and
 * {@code DELETE /actuator/caches/{name}} (for manual flushes).
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

    // Kişisel dashboard'lar — kullanıcı (+ gün penceresi) bazında anahtarlanır.
    public static final String ME_CUSTOMER_DASHBOARD  = "meCustomerDashboard";
    public static final String ME_AGENT_DASHBOARD     = "meAgentDashboard";

    // Yönetici/lead'in başka bir kullanıcının dashboard'unu görüntülemesi — kullanıcı +
    // scope (global/lead) + gün penceresi bazında anahtarlanır; kişisel cache'lerle karışmaz.
    public static final String USER_AGENT_DASHBOARD    = "userAgentDashboard";
    public static final String USER_CUSTOMER_DASHBOARD = "userCustomerDashboard";

    // Ürün bazlı dashboard — ürün + scope + gün penceresi bazında anahtarlanır.
    public static final String PRODUCT_DASHBOARD       = "productDashboard";

    // Rate limit config'leri; interceptor her istek öncesi okur.
    // @CacheEvict admin güncellemesinde anında geçersiz kılar; TTL sadece fallback.
    public static final String RATE_LIMIT_CONFIGS     = "rateLimitConfigs";

    /**
     * Builds a {@link CaffeineCacheManager} with a pre-declared name list.
     *
     * <p>A cache name absent from the list cannot be used with
     * {@code @Cacheable} — failing fast is preferred over silently no-oping.
     * The Caffeine configuration is {@code expireAfterWrite=5m},
     * {@code maximumSize=500}.
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
                ME_CUSTOMER_DASHBOARD,
                ME_AGENT_DASHBOARD,
                USER_AGENT_DASHBOARD,
                USER_CUSTOMER_DASHBOARD,
                PRODUCT_DASHBOARD,
                RATE_LIMIT_CONFIGS,
                "metrics"
        );
        manager.setCaffeine(Caffeine.newBuilder()
                .expireAfterWrite(5, TimeUnit.MINUTES)
                .maximumSize(500));
        return manager;
    }
}
