package com.ticketsystem.it_service_backend.util;

import org.springframework.security.oauth2.jwt.Jwt;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Helper that extracts and normalizes role/user fields from a Keycloak JWT.
 *
 * <p>Applies logic similar to the {@code JwtAuthenticationConverter} in
 * {@code SecurityConfig}; however the {@code ROLE_} prefix is stripped here
 * and the output is a plain {@code String} list rather than Spring
 * authorities. Used by the controller / service layer to answer
 * "what authority does the user have?".
 */
public class JwtUtils {

    /**
     * Extracts the role list from the token's {@code realm_access.roles}
     * claim.
     *
     * <p>All role names are upper-cased and the {@code ROLE_} prefix is
     * stripped if present, so {@code "ROLE_agent"} and {@code "agent"} both
     * come out as {@code "AGENT"}. If {@code realm_access} is missing or the
     * {@code roles} field is absent an empty list is returned.
     *
     * @param jwt the validated Keycloak access token
     * @return upper-cased role names without the prefix
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
