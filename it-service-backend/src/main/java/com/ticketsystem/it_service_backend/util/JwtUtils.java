package com.ticketsystem.it_service_backend.util;

import org.springframework.security.oauth2.jwt.Jwt;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class JwtUtils {

    @SuppressWarnings("unchecked")
    public static List<String> extractRoles(Jwt jwt) {
        Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
        List<String> rawRoles = new ArrayList<>();

        if (realmAccess != null && realmAccess.containsKey("roles")) {
            rawRoles = (List<String>) realmAccess.get("roles");
        }

        // Rolleri büyük harfe çevir ve "ROLE_" öneki olsa dahi temizle (Sadece AGENT, MANAGER kalsın)
        return rawRoles.stream()
                .map(String::toUpperCase)
                .map(role -> role.startsWith("ROLE_") ? role.replace("ROLE_", "") : role)
                .collect(Collectors.toList());
    }
}
