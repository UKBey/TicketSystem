package com.ticketsystem.llmservice.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Rate-limit ayarlari — application.yml'den okunur, hardcoded degil.
 *
 * <ul>
 *   <li>{@code enabled}: kill-switch (false -> interceptor pass-through).</li>
 *   <li>{@code maxRequests} / {@code durationSeconds}: token-bucket semantiği —
 *       "her durationSeconds icinde en fazla maxRequests istek". Varsayilan
 *       1/10sn (yani 10 saniyede 1 istek).</li>
 *   <li>{@code pathPatterns}: hangi URL desenlerine uygulanacak.</li>
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
    private List<String> pathPatterns = List.of("/api/ai/**");
}
