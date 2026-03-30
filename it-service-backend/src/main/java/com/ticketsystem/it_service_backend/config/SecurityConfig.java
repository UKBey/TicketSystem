package com.ticketsystem.it_service_backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity // Metod seviyesi yetkilendirme (@PreAuthorize) için eklendi
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable()) // REST API olduğu için CSRF korumasına gerek yok
                .authorizeHttpRequests(auth -> auth
                        // URL bazlı genel yetki (Token varsa her api'ye girebilir, detaylı kısıtlamaları metodlarda yapacağız)
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().permitAll())
                // Hafızada oturum tutmayı yasaklar (Stateless)
                .sessionManagement(sess -> sess.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // Gelen isteği OAuth2 Kaynak Sunucusu olarak doğrular ve Keycloak rollerini mapleme işlemini ayarlar
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> 
                        jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())));

        return http.build();
    }

    // Keycloak token'ı içindeki "realm_access" altındaki rolleri Spring Security formatına dönüştürür (ROLE_ prefixi ile)
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
                    .map(roleName -> "ROLE_" + roleName.toUpperCase()) // Rol yetkilendirmeleri için "ROLE_CUSTOMER" vb. çevirir
                    .map(SimpleGrantedAuthority::new)
                    .collect(Collectors.toList());
        });
        return converter;
    }
}