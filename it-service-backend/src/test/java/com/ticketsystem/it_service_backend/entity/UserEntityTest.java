package com.ticketsystem.it_service_backend.entity;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserEntityTest {

    @Test
    void onCreateSetsCreatedAt() {
        User user = User.builder()
                .id("user-1")
                .email("user@example.com")
                .fullName("Test User")
                .role("CUSTOMER")
                .build();

        ReflectionTestUtils.invokeMethod(user, "onCreate");

        assertNotNull(user.getCreatedAt());
    }

    @Test
    void builderUsesExpectedDefaults() {
        User user = User.builder()
                .id("user-2")
                .email("agent@example.com")
                .fullName("Agent User")
                .role("AGENT")
                .build();

        assertEquals(Boolean.TRUE, user.getIsActive());
        assertNotNull(user.getAuthorizedProducts());
        assertTrue(user.getAuthorizedProducts().isEmpty());
    }
}