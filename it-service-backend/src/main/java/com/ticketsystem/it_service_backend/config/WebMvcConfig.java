package com.ticketsystem.it_service_backend.config;

import com.ticketsystem.it_service_backend.interceptor.RateLimitInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Spring MVC configuration.
 *
 * <p>Registers {@link RateLimitInterceptor} and binds it to the paths that
 * are subject to rate limiting. The interceptor runs after the Security
 * Filter Chain, so JWT authentication is already complete when it executes.
 *
 * <p>SecurityConfig is intentionally left untouched; this class only configures
 * the MVC layer.
 *
 * <h3>Adding a new rate-limited endpoint</h3>
 * <ol>
 *   <li>Add a Flyway migration with a new INSERT into {@code rate_limit_configs}.</li>
 *   <li>Add the path pattern to {@code addInterceptors} below.</li>
 *   <li>Add the path-to-key mapping inside {@code RateLimitInterceptor.PATH_TO_KEY}.</li>
 * </ol>
 */
@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final RateLimitInterceptor rateLimitInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(rateLimitInterceptor)
                // Appends rate limit logic to all API endpoints for global spam protection.
                // The interceptor resolves the logical endpointKey from the URI, falling back to GLOBAL_API.
                .addPathPatterns("/api/v1/**");
        // To exclude endpoints, use .excludePathPatterns("/api/v1/some-path")
    }
}
