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
        assertEquals("IT Service Ticket System API", openAPI.getInfo().getTitle());
        assertEquals("Bilet (Ticket) yönetim sistemi için REST API dökümantasyonu.", openAPI.getInfo().getDescription());
        assertNotNull(openAPI.getComponents());
        assertNotNull(openAPI.getComponents().getSecuritySchemes().get("bearerAuth"));

        SecurityScheme securityScheme = openAPI.getComponents().getSecuritySchemes().get("bearerAuth");
        assertEquals(SecurityScheme.Type.HTTP, securityScheme.getType());
        assertEquals("bearer", securityScheme.getScheme());
        assertEquals("JWT", securityScheme.getBearerFormat());
        assertTrue(openAPI.getSecurity().stream().anyMatch(requirement -> requirement.containsKey("bearerAuth")));
    }
}