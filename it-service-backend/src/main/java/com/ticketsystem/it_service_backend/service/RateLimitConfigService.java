package com.ticketsystem.it_service_backend.service;

import com.ticketsystem.it_service_backend.config.RateLimitConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Service responsible for reading rate limit configurations from application.yml.
 *
 * <p>Config is injected via @ConfigurationProperties and remains immutable.
 * The interceptor calls {@link #getConfig} to fetch the global rate limit settings.
 */
@Component
@ConfigurationProperties(prefix = "app.rate-limit.global-api")
@RequiredArgsConstructor
public class RateLimitConfigService {

    private int maxRequests = 100;
    private int durationSeconds = 60;
    private boolean enabled = true;

    /** Setter populated by Spring's {@code @ConfigurationProperties} binder. */
    public void setMaxRequests(int maxRequests) {
        this.maxRequests = maxRequests;
    }

    /** Setter populated by Spring's {@code @ConfigurationProperties} binder. */
    public void setDurationSeconds(int durationSeconds) {
        this.durationSeconds = durationSeconds;
    }

    /** Setter populated by Spring's {@code @ConfigurationProperties} binder. */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * Returns the global rate limit config from application.yml.
     */
    public Optional<RateLimitConfig> getConfig(String endpointKey) {
        if (!"GLOBAL_API".equals(endpointKey)) {
            return Optional.empty();
        }
        
        RateLimitConfig config = RateLimitConfig.builder()
                .endpointKey("GLOBAL_API")
                .maxRequests(maxRequests)
                .durationSeconds(durationSeconds)
                .enabled(enabled)
                .build();
        return Optional.of(config);
    }
}
