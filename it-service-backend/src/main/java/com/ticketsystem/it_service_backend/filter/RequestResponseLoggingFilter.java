package com.ticketsystem.it_service_backend.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.log4j.Log4j2;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * A {@link OncePerRequestFilter} that writes one access-log line per HTTP
 * request.
 *
 * <p>Placed at the head of the chain with {@code @Order(1)} so the duration
 * measurement includes the cost of Spring Security and the interceptors.
 * The log level is chosen based on the status code: 5xx logs at
 * {@code ERROR}, 4xx at {@code WARN}, everything else at {@code INFO}.
 * Requests prefixed with {@code /actuator} (health probes) are skipped to
 * reduce log noise.
 */
@Log4j2
@Component
@Order(1)
public class RequestResponseLoggingFilter extends OncePerRequestFilter {
    private static final String LOG_FMT = "{} {} → {} ({}ms)";

    /**
     * Excludes health / metric probes from the log.
     *
     * @return {@code true} if the request should not be logged (only for
     *         {@code /actuator/*})
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return uri.startsWith("/actuator");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        long start = System.currentTimeMillis();
        String query = request.getQueryString();
        String uri = query != null ? request.getRequestURI() + "?" + query : request.getRequestURI();

        try {
            chain.doFilter(request, response);
        } finally {
            long duration = System.currentTimeMillis() - start;
            int status = response.getStatus();
            if (status >= 500) {
                log.error(LOG_FMT, request.getMethod(), uri, status, duration);
            } else if (status >= 400) {
                log.warn(LOG_FMT, request.getMethod(), uri, status, duration);
            } else {
                log.info(LOG_FMT, request.getMethod(), uri, status, duration);
            }
        }
    }
}
