package com.ticketsystem.llmservice.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Rate-limit settings — read from application.yml, not hardcoded.
 *
 * <ul>
 *   <li>{@code enabled}: kill-switch (false -> interceptor pass-through).</li>
 *   <li>{@code maxRequests} / {@code durationSeconds}: token-bucket semantics —
 *       "at most maxRequests requests per durationSeconds". Defaults to
 *       1/10s (one request every 10 seconds).</li>
 *   <li>{@code pathPatterns}: which URL patterns it applies to.</li>
 * </ul>
 */
@Component
@ConfigurationProperties(prefix = "app.rate-limit")
@Getter
@Setter
public class RateLimitProperties {

    private boolean enabled = true;
    private int maxRequests = 1;
    private int durationSeconds = 10;
    private List<String> pathPatterns = List.of("/api/v1/ai/**");
}
