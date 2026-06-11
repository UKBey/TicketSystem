package com.ticketsystem.it_service_backend.interceptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketsystem.it_service_backend.config.RateLimitConfig;
import com.ticketsystem.it_service_backend.service.RateLimitConfigService;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.distributed.BucketProxy;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.lettuce.core.api.StatefulRedisConnection;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Spring MVC interceptor — per-agent token-bucket rate limit, Redis-backed.
 *
 * <p>State is held in Redis via Bucket4j's {@link ProxyManager}. Buckets are
 * shared across replicas; a saturated bucket for one agent doesn't affect others.
 * Bucket-per-agent keys live under {@code endpointKey:agentId}; Bucket4j applies
 * a TTL based on the refill window so abandoned buckets are evicted automatically.
 *
 * <p>Runs after the Security Filter Chain, so JWT is already validated and the
 * agent identity is available via {@link SecurityContextHolder}.
 */
@Log4j2
@Component
@RequiredArgsConstructor
public class RateLimitInterceptor implements HandlerInterceptor {

    private final RateLimitConfigService rateLimitConfigService;
    private final ProxyManager<String> bucketProxyManager;
    private final StatefulRedisConnection<String, byte[]> bucketRedisConnection;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String GLOBAL_ENDPOINT_KEY = "GLOBAL_API";

    /**
     * Reads the configuration, extracts the user identity from the agent JWT
     * and consumes a token from the matching bucket.
     *
     * <p>If there is no token the client is allowed through as anonymous
     * (the Security Filter Chain enforces any required authentication).
     * When the bucket is empty the response is {@code HTTP 429} with a
     * {@code Retry-After} header and a JSON body. If the configuration is
     * disabled or missing the interceptor silently passes the request
     * through.
     *
     * @return {@code true} → let the request continue; {@code false} → 429
     *         was written, break the chain
     */
    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) throws IOException {

        String endpointKey = GLOBAL_ENDPOINT_KEY;

        Optional<RateLimitConfig> configOpt = rateLimitConfigService.getConfig(endpointKey);
        if (configOpt.isEmpty() || !configOpt.get().isEnabled()) {
            return true;
        }
        RateLimitConfig config = configOpt.get();

        String agentId = extractAgentId();
        if (agentId == null) {
            // Endpoint should be authenticated; treat as anonymous and pass through.
            log.warn("RateLimitInterceptor: could not extract agentId for endpoint {}", endpointKey);
            return true;
        }

        String bucketKey = endpointKey + ":" + agentId;
        Supplier<BucketConfiguration> configSupplier = () -> bucketConfigurationOf(config);
        BucketProxy bucket = bucketProxyManager.builder().build(bucketKey, configSupplier);

        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);

        if (probe.isConsumed()) {
            log.debug("Rate limit OK -- agent={} endpoint={} remaining={}",
                    agentId, endpointKey, probe.getRemainingTokens());
            return true;
        }

        long retryAfterSeconds = probe.getNanosToWaitForRefill() / 1_000_000_000L;
        log.warn("Rate limit EXCEEDED -- agent={} endpoint={} retryAfter={}s",
                agentId, endpointKey, retryAfterSeconds);

        response.setStatus(429);
        response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(Map.of(
                "error", "RATE_LIMIT_EXCEEDED",
                "retryAfterSeconds", retryAfterSeconds
        )));
        return false;
    }

    /**
     * Drops every Redis bucket key that belongs to the given endpoint. Call this
     * when an admin updates a config so the new limits take effect immediately
     * for everyone — otherwise old buckets keep their old capacity until they expire.
     *
     * <p>SCAN is used instead of KEYS to avoid blocking the Redis event loop
     * when there are many active buckets.
     */
    public void invalidateBuckets(String endpointKey) {
        String pattern = endpointKey + ":*";
        long deleted = 0;
        try {
            var commands = bucketRedisConnection.sync();
            var cursor = io.lettuce.core.ScanCursor.INITIAL;
            io.lettuce.core.ScanArgs args = io.lettuce.core.ScanArgs.Builder.matches(pattern).limit(100);
            while (true) {
                var result = commands.scan(cursor, args);
                if (!result.getKeys().isEmpty()) {
                    deleted += commands.del(result.getKeys().toArray(new String[0]));
                }
                if (result.isFinished()) break;
                cursor = io.lettuce.core.ScanCursor.of(result.getCursor());
            }
        } catch (Exception e) {
            log.error("invalidateBuckets failed for pattern={}: {}", pattern, e.getMessage());
            return;
        }
        log.info("Invalidated {} bucket key(s) for endpoint '{}'", deleted, endpointKey);
    }

    // ------------------------------------------------------------------
    // Private helpers
    // ------------------------------------------------------------------

    private String extractAgentId() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt) {
            return jwt.getSubject();
        }
        return null;
    }

    /**
     * Token-bucket configuration matching the legacy semantics:
     * {@code refillIntervally} refills the full capacity in one shot every
     * {@code durationSeconds} — i.e. a fixed-window "N requests per period".
     */
    private BucketConfiguration bucketConfigurationOf(RateLimitConfig config) {
        Bandwidth limit = Bandwidth.builder()
                .capacity(config.getMaxRequests())
                .refillIntervally(
                        config.getMaxRequests(),
                        Duration.ofSeconds(config.getDurationSeconds())
                )
                .build();
        return BucketConfiguration.builder().addLimit(limit).build();
    }
}
