package com.ticketsystem.it_service_backend.util;

import org.springframework.security.oauth2.jwt.Jwt;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Keycloak JWT'sinden rol/kullanici alanlarini normalize ederek cikaran
 * yardimci sinif.
 *
 * <p>{@code SecurityConfig}'in {@code JwtAuthenticationConverter}'i ile
 * benzer mantigi uygular; ancak burada {@code ROLE_} on eki temizlenir ve
 * cikti Spring authority'leri yerine duz {@code String} listesi olur.
 * Controller / servis katmaninda "kullanicinin yetkisi nedir?" sorusuna
 * cevap vermek icin kullanilir.
 */
public class JwtUtils {

    /**
     * Token'in {@code realm_access.roles} claim'inden rol listesini cikarir.
     *
     * <p>Tum rol isimleri buyuk harfe cevrilir ve {@code ROLE_} on eki varsa
     * kaldirilir; boylece {@code "ROLE_agent"} ve {@code "agent"} ayni cikar:
     * {@code "AGENT"}. {@code realm_access} yoksa veya {@code roles} alani
     * eksikse bos liste doner.
     *
     * @param jwt dogrulanmis Keycloak access token'i
     * @return on eksiz, buyuk harfli rol adlari
     */
    @SuppressWarnings("unchecked")
    public static List<String> extractRoles(Jwt jwt) {
        Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
        List<String> rawRoles = new ArrayList<>();

        if (realmAccess != null && realmAccess.containsKey("roles")) {
            rawRoles = (List<String>) realmAccess.get("roles");
        }

        // Rol isimlerini normalize eder; ROLE_ onekini temizleyip tek formatta dondurur.
        return rawRoles.stream()
                .map(String::toUpperCase)
                .map(role -> role.startsWith("ROLE_") ? role.replace("ROLE_", "") : role)
                .collect(Collectors.toList());
    }
}
