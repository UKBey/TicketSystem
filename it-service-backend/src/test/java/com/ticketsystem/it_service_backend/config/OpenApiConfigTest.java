package com.ticketsystem.it_service_backend.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenApiConfigTest {

    @Test
    void customOpenApiDefinesBearerAuthentication() {
        OpenAPI openAPI = new OpenApiConfig().customOpenAPI();

        assertNotNull(openAPI.getInfo());
        assertEquals("IT Service Desk — Ticket Management API", openAPI.getInfo().getTitle());
        assertTrue(openAPI.getInfo().getDescription().contains("Kurumsal IT destek bilet yönetim sistemi"));
        // Surum `make set-version` ile guncellenir; tam degeri degil semver bicimini dogrula
        assertTrue(openAPI.getInfo().getVersion().matches("\\d+\\.\\d+\\.\\d+.*"));

        // Contact bilgisi
        assertNotNull(openAPI.getInfo().getContact());
        assertEquals("IT Service Desk Ekibi", openAPI.getInfo().getContact().getName());

        // Lisans bilgisi
        assertNotNull(openAPI.getInfo().getLicense());
        assertEquals("MIT License", openAPI.getInfo().getLicense().getName());

        // Sunucu tanımları
        assertNotNull(openAPI.getServers());
        assertEquals(2, openAPI.getServers().size());

        // Tag tanımları
        assertNotNull(openAPI.getTags());
        assertTrue(openAPI.getTags().size() >= 9);

        // Güvenlik şeması
        assertNotNull(openAPI.getComponents());
        assertNotNull(openAPI.getComponents().getSecuritySchemes().get("bearerAuth"));

        SecurityScheme securityScheme = openAPI.getComponents().getSecuritySchemes().get("bearerAuth");
        assertEquals(SecurityScheme.Type.HTTP, securityScheme.getType());
        assertEquals("bearer", securityScheme.getScheme());
        assertEquals("JWT", securityScheme.getBearerFormat());
        assertNotNull(securityScheme.getDescription());
        assertTrue(openAPI.getSecurity().stream().anyMatch(requirement -> requirement.containsKey("bearerAuth")));

        // Harici doküman
        assertNotNull(openAPI.getExternalDocs());
        assertTrue(openAPI.getExternalDocs().getUrl().contains("github.com"));
    }
}