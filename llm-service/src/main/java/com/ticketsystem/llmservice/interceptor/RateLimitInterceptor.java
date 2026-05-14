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
 * Per-client (IP bazli) rate-limit interceptor — Redis'te Bucket4j ProxyManager
 * ile yonetilir. llm-service'in herkese acik AI endpoint'lerini Groq API
 * kotasini koruyacak sekilde sinirlar.
 *
 * <p>Limitler {@link RateLimitProperties} araciligiyla configlenebilir
 * (varsayilan: 10 saniyede 1 istek).
 *
 * <p>Istemci kimlik tespiti:
 * <ol>
 *   <li>{@code X-Forwarded-For} header'inin ilk degeri (nginx proxy ekler).</li>
 *   <li>Yoksa {@link HttpServletRequest#getRemoteAddr()}.</li>
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
     * Reverse-proxy arkasinda calistigimiz icin gercek istemci IP'si
     * {@code X-Forwarded-For} header'inin ilk girisidir. Yoksa servlet'in
     * remote-addr'i kullanilir.
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
     * "N istek / D saniye" yapisini Bucket4j'nin {@code Refill.intervally}
     * (kova D saniyede bir tam kapasite ile yenilenir) ile karsilar — yani
     * fixed-window davranisi.
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
