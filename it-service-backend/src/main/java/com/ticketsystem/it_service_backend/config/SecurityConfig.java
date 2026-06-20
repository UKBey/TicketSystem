package com.ticketsystem.it_service_backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import java.util.stream.Collectors;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.util.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * OAuth2 Resource Server configuration — the single source of truth for the
 * backend's entire security policy.
 *
 * <p>Stateless JWT validation, CSRF disabled, method-level security enabled.
 * There are two parallel authorization paths:
 * <ol>
 *   <li><b>User endpoints</b> — a {@code Bearer} JWT is required and
 *       {@code realm_access.roles} is translated into {@code ROLE_*}
 *       authorities.</li>
 *   <li><b>Internal endpoints</b> ({@code /api/v1/internal/**}) — protected by
 *       a static {@code X-Internal-Token} header instead of a JWT; used only by
 *       the KIE Server callback.</li>
 * </ol>
 *
 * <p>Swagger, actuator health/info/metrics and the auth entry endpoints are
 * exposed anonymously. {@code /actuator/caches/**} is reachable only by admin
 * roles.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Value("${jbpm.kie-server.callback-token}")
    private String internalApiToken;

    /**
     * S-10 — Explicit CORS allow-list. In this project the frontend and backend
     * are served from the same origin through nginx, so there is no practical
     * CORS scenario today; the bean is still defined so that any future
     * mobile or third-party client must be explicitly whitelisted. Relying on
     * defaults risks scope creep.
     *
     * Origins can be extended via {@code app.cors.allowed-origins}; the default
     * only covers the main proxy URLs.
     */
    @Value("${app.cors.allowed-origins:http://localhost,http://ticketsystem.local}")
    private String allowedOrigins;

    /**
     * S-10 — Whitelist-based CORS configuration.
     *
     * <p>Applied only to {@code /api/v1/**}. Allows only the {@code GET/POST/
     * PUT/PATCH/DELETE/OPTIONS} methods and the {@code Authorization},
     * {@code Content-Type}, {@code X-Requested-With} headers.
     * {@code allowCredentials=false} — authentication arrives via the Bearer
     * header, no cookies are used. Preflight responses are cached for 1 hour.
     *
     * @return CORS source configuration applied to all API paths
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration cfg = new CorsConfiguration();
        cfg.setAllowedOrigins(Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList());
        cfg.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        cfg.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Requested-With"));
        // Credentials acikca false — JWT bearer header ile geliyor, cookie yok.
        cfg.setAllowCredentials(false);
        cfg.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource src = new UrlBasedCorsConfigurationSource();
        src.registerCorsConfiguration("/api/v1/**", cfg);
        return src;
    }

    /**
     * The primary {@link SecurityFilterChain} — a single filter chain that
     * combines the anonymous, internal-token and JWT paths.
     *
     * <p>Permit-list: Swagger, actuator health/info/metrics, the auth
     * endpoints and the WebSocket handshake. {@code /api/v1/internal/**}
     * goes through {@link #hasValidInternalToken(String)}. Every remaining
     * endpoint requires a {@code Bearer} JWT and the session is marked as
     * {@link SessionCreationPolicy#STATELESS}.
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
        .cors(Customizer.withDefaults())
        .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
            // Dokumantasyon, saglik, metrik ve ilk giris endpoint'leri anonim erisime aciktir.
            .requestMatchers(
                "/v3/api-docs/**",
                "/swagger-ui/**",
                "/swagger-ui.html",
                "/actuator/health",
                "/actuator/health/**",
                "/actuator/info",
                "/actuator/metrics",
                "/actuator/metrics/**",
                "/api/v1/auth/login",
                "/api/v1/auth/register",
                "/ws/**")
            .permitAll()

            // Internal endpoint'ler sadece servisler arasi token ile erisilebilir.
            .requestMatchers("/api/v1/internal/**")
            .access((authentication, context) ->
                new AuthorizationDecision(hasValidInternalToken(context.getRequest().getHeader("X-Internal-Token"))))

            // Actuator cache yonetimi: env-driven SLA degisikligi sonrasi
            // DELETE /actuator/caches/{name} ile manuel flush yapilir; sadece
            // admin rolleri erisebilir.
            .requestMatchers("/actuator/caches/**")
            .hasRole("ADMIN")

            // Geri kalan tum endpoint'ler icin JWT dogrulamasi zorunludur.
            .anyRequest().authenticated())
                .sessionManagement(sess -> sess.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> 
                        jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())));

        return http.build();
    }

    /**
     * Translates the Keycloak {@code realm_access.roles} list into Spring
     * Security {@code ROLE_*} authorities.
     *
     * <p>Role names are normalized to upper case; for example the
     * {@code "lead_agent"} role becomes the {@code "ROLE_LEAD_AGENT"}
     * authority. If the token does not carry a {@code realm_access} claim an
     * empty authority list is returned (the request is authenticated but
     * unauthorized rather than anonymous).
     */
    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
            if (realmAccess == null || !realmAccess.containsKey("roles")) {
                return List.of();
            }
            @SuppressWarnings("unchecked")
            Collection<String> roles = (Collection<String>) realmAccess.get("roles");
            return roles.stream()
                    .map(roleName -> "ROLE_" + roleName.toUpperCase())
                    .map(SimpleGrantedAuthority::new)
                    .collect(Collectors.toList());
        });
        return converter;
    }

    private boolean hasValidInternalToken(String headerToken) {
        if (!StringUtils.hasText(headerToken) || !StringUtils.hasText(internalApiToken)) {
            return false;
        }
        // Constant-time karşılaştırma — String.equals early-return yapıyor, bu da
        // timing attack ile karakter-karakter token tahminine zemin hazırlar.
        return MessageDigest.isEqual(
                headerToken.getBytes(StandardCharsets.UTF_8),
                internalApiToken.getBytes(StandardCharsets.UTF_8));
    }
}