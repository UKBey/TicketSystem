package com.ticketsystem.llmservice.config;

import com.ticketsystem.llmservice.interceptor.RateLimitInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Spring MVC config — registers the {@link RateLimitInterceptor} on the paths
 * matched by {@link RateLimitProperties#getPathPatterns()}.
 */
@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final RateLimitInterceptor rateLimitInterceptor;
    private final RateLimitProperties rateLimitProperties;

    /**
     * Registers the {@link RateLimitInterceptor} on the path patterns read
     * from config.
     *
     * @param registry Spring MVC interceptor registry
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(rateLimitInterceptor)
                .addPathPatterns(rateLimitProperties.getPathPatterns());
    }
}
