package com.ticketsystem.it_service_backend.config;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SecurityConfigTest {

    @Test
    void jwtAuthenticationConverterMapsRealmRolesToAuthorities() {
        SecurityConfig securityConfig = new SecurityConfig();
        JwtAuthenticationConverter converter = securityConfig.jwtAuthenticationConverter();

        Jwt jwt = mock(Jwt.class);
        when(jwt.getClaimAsMap("realm_access")).thenReturn(Map.of("roles", List.of("lead_agent", "agent")));

        List<String> authorities = converter.convert(jwt).getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();

        assertTrue(authorities.containsAll(List.of("ROLE_LEAD_AGENT", "ROLE_AGENT")));
    }

    @Test
    void jwtAuthenticationConverterMapsMultipleRoles() {
        SecurityConfig securityConfig = new SecurityConfig();
        JwtAuthenticationConverter converter = securityConfig.jwtAuthenticationConverter();

        Jwt jwt = mock(Jwt.class);
        when(jwt.getClaimAsMap("realm_access")).thenReturn(Map.of("roles", List.of("admin", "manager")));

        List<String> authorities = converter.convert(jwt).getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();

        assertTrue(authorities.contains("ROLE_ADMIN"));
        assertTrue(authorities.contains("ROLE_MANAGER"));
    }

    @Test
    void hasValidInternalTokenAcceptsMatchingToken() {
        SecurityConfig securityConfig = new SecurityConfig();
        ReflectionTestUtils.setField(securityConfig, "internalApiToken", "secret-token");

        boolean result = (Boolean) ReflectionTestUtils.invokeMethod(
                securityConfig,
                "hasValidInternalToken",
                "secret-token"
        );

        assertTrue(result);
    }

    @Test
    void hasValidInternalTokenRejectsMissingToken() {
        SecurityConfig securityConfig = new SecurityConfig();
        ReflectionTestUtils.setField(securityConfig, "internalApiToken", "secret-token");

        boolean result = (Boolean) ReflectionTestUtils.invokeMethod(
                securityConfig,
                "hasValidInternalToken",
                (Object) null
        );

        assertFalse(result);
    }

    @Test
    void hasValidInternalTokenRejectsMismatch() {
        SecurityConfig securityConfig = new SecurityConfig();
        ReflectionTestUtils.setField(securityConfig, "internalApiToken", "expected");

        boolean result = (Boolean) ReflectionTestUtils.invokeMethod(
                securityConfig,
                "hasValidInternalToken",
                "wrong"
        );

        assertFalse(result);
    }

    @Test
    void hasValidInternalTokenRejectsEmptyExpected() {
        SecurityConfig securityConfig = new SecurityConfig();
        ReflectionTestUtils.setField(securityConfig, "internalApiToken", "");

        boolean result = (Boolean) ReflectionTestUtils.invokeMethod(
                securityConfig,
                "hasValidInternalToken",
                "anything"
        );

        assertFalse(result);
    }

    @Test
    void hasValidInternalTokenRejectsBlankHeader() {
        SecurityConfig securityConfig = new SecurityConfig();
        ReflectionTestUtils.setField(securityConfig, "internalApiToken", "secret-token");

        boolean result = (Boolean) ReflectionTestUtils.invokeMethod(
                securityConfig,
                "hasValidInternalToken",
                "   "
        );

        assertFalse(result);
    }

    @Test
    void jwtAuthenticationConverter_returnsEmpty_whenRealmAccessMissing() {
        SecurityConfig securityConfig = new SecurityConfig();
        JwtAuthenticationConverter converter = securityConfig.jwtAuthenticationConverter();

        Jwt jwt = mock(Jwt.class);
        when(jwt.getClaimAsMap("realm_access")).thenReturn(null);

        List<String> authorities = converter.convert(jwt).getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();

        assertTrue(authorities.stream().noneMatch(a -> a.startsWith("ROLE_")));
    }

    @Test
    void jwtAuthenticationConverter_returnsEmpty_whenRolesKeyMissing() {
        SecurityConfig securityConfig = new SecurityConfig();
        JwtAuthenticationConverter converter = securityConfig.jwtAuthenticationConverter();

        Jwt jwt = mock(Jwt.class);
        when(jwt.getClaimAsMap("realm_access")).thenReturn(Map.of("other", "value"));

        List<String> authorities = converter.convert(jwt).getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();

        assertTrue(authorities.stream().noneMatch(a -> a.startsWith("ROLE_")));
    }
}