package com.ticketsystem.it_service_backend.interceptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketsystem.it_service_backend.entity.RateLimitConfig;
import com.ticketsystem.it_service_backend.service.RateLimitConfigService;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.Refill;
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
import java.util.concurrent.ConcurrentHashMap;

/**
 * Spring MVC interceptor that enforces per-agent token-bucket rate limits.
 *
 * <p>Runs <em>after</em> the Security Filter Chain, so the JWT is already validated
 * and the agent's identity is available via {@link SecurityContextHolder}.
 *
 * <p>Bucket granularity: {@code endpointKey + agentId} -- one bucket per agent
 * per endpoint. A saturated bucket for one agent does not affect others.
 *
 * <p>Config is read from {@link RateLimitConfigService} (Caffeine-cached).
 * When an admin updates a config, {@link #invalidateBuckets(String)} must be called
 * to flush the in-process buckets so the new limits take effect immediately.
 */
@Log4j2
@Component
@RequiredArgsConstructor
public class RateLimitInterceptor implements HandlerInterceptor {

    private final RateLimitConfigService rateLimitConfigService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Two-level map: endpointKey -> (agentId -> Bucket).
     * ConcurrentHashMap is thread-safe for concurrent request handling.
     * Buckets are created lazily on first request.
     */
    private final ConcurrentHashMap<String, ConcurrentHashMap<String, Bucket>> buckets =
            new ConcurrentHashMap<>();

    /**
     * Maps a request URI pattern to its logical endpoint key.
     * Pattern matching uses simple prefix/suffix checks to avoid regex overhead.
     */
    private static final Map<String, String> PATH_TO_KEY = Map.of(
            "/api/tickets/*/claim", "CLAIM_TICKET"
    );

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) throws IOException {

        // 1. Resolve endpointKey from request URI
        String endpointKey = resolveEndpointKey(request.getRequestURI());
        if (endpointKey == null) {
            return true; // path not covered by any rate limit config
        }

        // 2. Load config (Caffeine-cached); pass through if missing or disabled
        Optional<RateLimitConfig> configOpt = rateLimitConfigService.getConfig(endpointKey);
        if (configOpt.isEmpty() || !configOpt.get().isEnabled()) {
            return true;
        }
        RateLimitConfig config = configOpt.get();

        // 3. Extract agentId from the validated JWT (Security Filter Chain has already run)
        String agentId = extractAgentId();
        if (agentId == null) {
            // Should not happen -- endpoint is secured; treat as anonymous and pass
            log.warn("RateLimitInterceptor: could not extract agentId for endpoint {}", endpointKey);
            return true;
        }

        // 4. Get or create bucket for this agent + endpoint combination
        Bucket bucket = buckets
                .computeIfAbsent(endpointKey, k -> new ConcurrentHashMap<>())
                .computeIfAbsent(agentId, k -> buildBucket(config));

        // 5. Attempt to consume one token
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);

        if (probe.isConsumed()) {
            log.debug("Rate limit OK -- agent={} endpoint={} remaining={}",
                    agentId, endpointKey, probe.getRemainingTokens());
            return true;
        }

        // 6. Limit exceeded -- return 429 with Retry-After
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
     * Clears all in-process buckets for the given endpoint key.
     * Must be called whenever the DB config for that key is updated so
     * the new limits are picked up on the next request.
     *
     * @param endpointKey the logical key (e.g. "CLAIM_TICKET")
     */
    public void invalidateBuckets(String endpointKey) {
        ConcurrentHashMap<String, Bucket> removed = buckets.remove(endpointKey);
        int count = removed != null ? removed.size() : 0;
        log.info("Invalidated {} bucket(s) for endpoint '{}'", count, endpointKey);
    }

    // ------------------------------------------------------------------
    // Private helpers
    // ------------------------------------------------------------------

    /**
     * Resolves the logical endpoint key for a given request URI.
     * Currently uses simple wildcard matching; extend for more patterns.
     */
    private String resolveEndpointKey(String uri) {
        for (Map.Entry<String, String> entry : PATH_TO_KEY.entrySet()) {
            if (matchesPattern(uri, entry.getKey())) {
                return entry.getValue();
            }
        }
        return null;
    }

    /**
     * Matches a URI against a simple glob-style pattern where {@code *}
     * matches any single path segment (no slashes).
     * Example: {@code /api/tickets/42/claim} matches {@code /api/tickets/*&#47;claim}
     */
    private boolean matchesPattern(String uri, String pattern) {
        // Split on the wildcard and check prefix / suffix
        int starIdx = pattern.indexOf('*');
        if (starIdx < 0) {
            return uri.equals(pattern);
        }
        String prefix = pattern.substring(0, starIdx);
        String suffix = pattern.substring(starIdx + 1);
        if (!uri.startsWith(prefix) || !uri.endsWith(suffix)) {
            return false;
        }
        // Ensure the wildcard segment contains no additional slashes
        String middle = uri.substring(prefix.length(), uri.length() - suffix.length());
        return !middle.contains("/");
    }

    /**
     * Extracts the subject (Keycloak user UUID) from the JWT stored in the
     * security context. Returns null if the principal is not a {@link Jwt}.
     */
    private String extractAgentId() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt) {
            return jwt.getSubject();
        }
        return null;
    }

    /**
     * Builds a new {@link Bucket} with a classic token-bucket algorithm.
     * Refill#intervally refills the full capacity at once after
     * durationSeconds -- not drip-by-drip -- which matches the
     * "N requests per window" semantics expected by the admin configuration.
     */
    private Bucket buildBucket(RateLimitConfig config) {
        Bandwidth limit = Bandwidth.classic(
                config.getMaxRequests(),
                Refill.intervally(
                        config.getMaxRequests(),
                        Duration.ofSeconds(config.getDurationSeconds())
                )
        );
        return Bucket.builder().addLimit(limit).build();
    }
}
