package com.ticketsystem.llmservice.config;

import com.ticketsystem.llmservice.interceptor.RateLimitInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Spring MVC config — {@link RateLimitInterceptor}'i
 * {@link RateLimitProperties#getPathPatterns()} ile eslesen yollar uzerine kaydeder.
 */
@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final RateLimitInterceptor rateLimitInterceptor;
    private final RateLimitProperties rateLimitProperties;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(rateLimitInterceptor)
                .addPathPatterns(rateLimitProperties.getPathPatterns());
    }
}
