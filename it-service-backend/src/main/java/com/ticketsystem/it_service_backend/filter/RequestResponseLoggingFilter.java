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
 * Her HTTP istegi icin bir satir erisim logu yazan {@link OncePerRequestFilter}.
 *
 * <p>{@code @Order(1)} ile zincirin en basina yerlestirilir, boylece sure
 * olcumune Spring Security ve interceptor'larin maliyeti de dahil olur. Status
 * koduna gore log seviyesi ayarlanir: 5xx {@code ERROR}, 4xx {@code WARN}, geri
 * kalan {@code INFO}. {@code /actuator} prefixli istekler (saglik probe'lari) log
 * gurultusunu azaltmak icin atlanir.
 */
@Log4j2
@Component
@Order(1)
public class RequestResponseLoggingFilter extends OncePerRequestFilter {

    /**
     * Saglik / metrik probe'larini logdan dislar.
     *
     * @return {@code true} ise istek loglanmaz (yalnizca {@code /actuator/*})
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
                log.error("{} {} → {} ({}ms)", request.getMethod(), uri, status, duration);
            } else if (status >= 400) {
                log.warn("{} {} → {} ({}ms)", request.getMethod(), uri, status, duration);
            } else {
                log.info("{} {} → {} ({}ms)", request.getMethod(), uri, status, duration);
            }
        }
    }
}
