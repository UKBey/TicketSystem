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
import java.util.stream.Collectors;

/**
 * OAuth2 Resource Server yapilandirmasi — backend'in tum guvenlik politikasinin
 * tek dogru kaynagi.
 *
 * <p>Stateless JWT dogrulamasi, CSRF kapali, method-level security acik. Iki
 * paralel yetkilendirme yolu vardir:
 * <ol>
 *   <li><b>Kullanici endpoint'leri</b> — {@code Bearer} JWT zorunludur;
 *       {@code realm_access.roles} {@code ROLE_*} authority'lerine cevrilir.</li>
 *   <li><b>Internal endpoint'ler</b> ({@code /api/v1/internal/**}) — JWT yerine
 *       sabit {@code X-Internal-Token} header'iyla korunur; sadece KIE Server
 *       callback'i icin kullanilir.</li>
 * </ol>
 *
 * <p>Swagger, actuator health/info/metrics ve auth giris endpoint'leri anonime
 * acilir. {@code /actuator/caches/**} sadece admin rolleriyle erisilebilir.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Value("${jbpm.kie-server.callback-token}")
    private String internalApiToken;

    /**
     * S-10 — Explicit CORS allow-list. Bu projede frontend ve backend nginx ile
     * ayni origin'den servis edildigi icin pratik bir CORS senaryosu yok; bean
     * yine de tanimli ki ileride bir mobile/3rd-party istemci eklenirse origin
     * acikca whitelist'lensin. Default'a guvenmek kapsam disi kayma riski tasir.
     *
     * Origin'ler {@code app.cors.allowed-origins} ile genisletilebilir; default
     * yalniz ana proxy URL'lerini kapsar.
     */
    @Value("${app.cors.allowed-origins:http://localhost,http://ticketsystem.local}")
    private String allowedOrigins;

    /**
     * S-10 — Whitelist tabanli CORS yapilandirmasi.
     *
     * <p>Yalnizca {@code /api/v1/**} icin uygulanir. Sadece {@code GET/POST/PUT/
     * PATCH/DELETE/OPTIONS} method'larina ve {@code Authorization},
     * {@code Content-Type}, {@code X-Requested-With} header'larina izin verilir.
     * {@code allowCredentials=false} — kimlik dogrulama Bearer header ile gelir,
     * cookie kullanilmaz. Preflight cevabi 1 saat cache'lenir.
     *
     * @return tum API path'lerine uygulanan CORS kaynak yapilandirmasi
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration cfg = new CorsConfiguration();
        cfg.setAllowedOrigins(Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList()));
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
     * Ana {@link SecurityFilterChain} — anonim, internal-token ve JWT yollarini
     * birlestiren tek filter zinciri.
     *
     * <p>Permit-list: Swagger, actuator health/info/metrics, auth endpoint'leri ve
     * WebSocket handshake. {@code /api/v1/internal/**}
     * {@link #hasValidInternalToken(String)} kontrolune girer. Geri kalan tum
     * endpoint'lerde {@code Bearer} JWT zorunludur ve session
     * {@link SessionCreationPolicy#STATELESS} olarak isaretlenir.
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
                "/api/v1/auth/forgot-password",
                "/api/v1/auth/reset-password",
                "/api/v1/auth/reset-password/validate",
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
            .hasAnyRole("AGENT_ADMIN", "MANAGER")

            // Geri kalan tum endpoint'ler icin JWT dogrulamasi zorunludur.
            .anyRequest().authenticated())
                .sessionManagement(sess -> sess.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> 
                        jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())));

        return http.build();
    }

    /**
     * Keycloak {@code realm_access.roles} listesini Spring Security
     * {@code ROLE_*} authority'lerine cevirir.
     *
     * <p>Rol isimleri buyuk harfe normalize edilir; ornek olarak
     * {@code "agent_admin"} authority {@code "ROLE_AGENT_ADMIN"} olur. Eger token
     * {@code realm_access} claim'i tasimiyorsa bos authority listesi dondurulur
     * (anonim degil, ama yetkisiz).
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