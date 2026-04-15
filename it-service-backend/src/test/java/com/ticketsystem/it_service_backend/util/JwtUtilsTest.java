package com.ticketsystem.it_service_backend.util;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JwtUtilsTest {

    @Test
    void extractRolesNormalizesRoleNames() {
        Jwt jwt = mock(Jwt.class);
        when(jwt.getClaimAsMap("realm_access")).thenReturn(Map.of(
                "roles",
                List.of("role_user", "ROLE_agent", "Manager")
        ));

        assertEquals(List.of("USER", "AGENT", "MANAGER"), JwtUtils.extractRoles(jwt));
    }

    @Test
    void extractRolesReturnsEmptyListWhenRealmAccessMissing() {
        Jwt jwt = mock(Jwt.class);
        when(jwt.getClaimAsMap("realm_access")).thenReturn(null);

        assertTrue(JwtUtils.extractRoles(jwt).isEmpty());
    }
}