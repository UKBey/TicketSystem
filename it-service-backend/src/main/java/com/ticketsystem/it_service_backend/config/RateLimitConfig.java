package com.ticketsystem.it_service_backend.config;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/**
 * Immutable rate-limit settings for a logical endpoint.
 *
 * <p>The limits live entirely in configuration ({@code application.yml} →
 * {@code app.rate-limit.global-api.*}, overridable through the
 * {@code RATE_LIMIT_GLOBAL_*} environment variables) — <strong>not</strong> in
 * the database. The former {@code rate_limit_configs} table was dropped in
 * migration V38; this is now a plain value object built by
 * {@code RateLimitConfigService} from the bound properties.
 */
@Getter
@Builder
@AllArgsConstructor
public class RateLimitConfig {

    private final String endpointKey;
    private final int maxRequests;
    private final int durationSeconds;
    private final boolean enabled;
}
