package com.ticketsystem.llmservice.interceptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketsystem.llmservice.config.RateLimitProperties;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.Refill;
import io.github.bucket4j.distributed.BucketProxy;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Per-client (IP-based) rate-limit interceptor — managed in Redis via the
 * Bucket4j ProxyManager. Throttles llm-service's public AI endpoints in a way
 * that protects the Groq API quota.
 *
 * <p>Limits are configurable through {@link RateLimitProperties}
 * (default: one request per 10 seconds).
 *
 * <p>Client identification:
 * <ol>
 *   <li>The first value of the {@code X-Forwarded-For} header (added by the nginx proxy).</li>
 *   <li>Otherwise {@link HttpServletRequest#getRemoteAddr()}.</li>
 * </ol>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RateLimitInterceptor implements HandlerInterceptor {

    private final RateLimitProperties properties;
    private final ProxyManager<String> bucketProxyManager;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String BUCKET_KEY_PREFIX = "llm-rate-limit:";

    /**
     * Runs before every request; tries to consume one token from the client's
     * Bucket4j bucket. If a token is available the request proceeds, otherwise
     * a {@code 429 Too Many Requests} response is written and the
     * {@code Retry-After} header is added.
     *
     * @param request  incoming HTTP request
     * @param response HTTP response to be written (used directly on 429)
     * @param handler  target handler object (unused)
     * @return {@code true} if the request may continue, {@code false} when the limit is exceeded
     * @throws IOException if writing the response body fails
     */
    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) throws IOException {

        if (!properties.isEnabled()) {
            return true;
        }

        String clientId = extractClientId(request);
        String bucketKey = BUCKET_KEY_PREFIX + clientId;

        Supplier<BucketConfiguration> configSupplier = this::buildBucketConfiguration;
        BucketProxy bucket = bucketProxyManager.builder().build(bucketKey, configSupplier);

        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);

        if (probe.isConsumed()) {
            log.debug("LLM rate limit OK -- client={} remaining={}", clientId, probe.getRemainingTokens());
            return true;
        }

        long retryAfterSeconds = Math.max(1, probe.getNanosToWaitForRefill() / 1_000_000_000L);
        log.warn("LLM rate limit EXCEEDED -- client={} retryAfter={}s", clientId, retryAfterSeconds);

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
     * Because we run behind a reverse proxy, the real client IP is the first
     * entry of the {@code X-Forwarded-For} header. If it is missing the
     * servlet's remote-addr is used.
     */
    private String extractClientId(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            int comma = forwardedFor.indexOf(',');
            String first = comma < 0 ? forwardedFor : forwardedFor.substring(0, comma);
            return first.trim();
        }
        String remoteAddr = request.getRemoteAddr();
        return remoteAddr == null ? "unknown" : remoteAddr;
    }

    /**
     * Maps the "N requests / D seconds" model onto Bucket4j's
     * {@code Refill.intervally} (the bucket is fully refilled every D seconds) —
     * i.e. fixed-window behavior.
     */
    private BucketConfiguration buildBucketConfiguration() {
        Bandwidth limit = Bandwidth.classic(
                properties.getMaxRequests(),
                Refill.intervally(
                        properties.getMaxRequests(),
                        Duration.ofSeconds(properties.getDurationSeconds())
                )
        );
        return BucketConfiguration.builder().addLimit(limit).build();
    }
}
