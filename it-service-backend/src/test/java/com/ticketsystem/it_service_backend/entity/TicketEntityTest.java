package com.ticketsystem.it_service_backend.entity;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class TicketEntityTest {

    @Test
    void onCreateSetsDefaultStatusAndCreatedAt() {
        Ticket ticket = Ticket.builder()
                .title("Network issue")
                .description("Internet is down")
                .priority("HIGH")
                .customerId("customer-1")
                .build();

        assertNull(ticket.getCreatedAt());
        assertNull(ticket.getStatus());

        ReflectionTestUtils.invokeMethod(ticket, "onCreate");

        assertNotNull(ticket.getCreatedAt());
        assertEquals("NEW", ticket.getStatus());
    }

    @Test
    void builderInitializesSlaDefaults() {
        Ticket ticket = Ticket.builder()
                .title("Printer issue")
                .description("Paper jam")
                .priority("LOW")
                .customerId("customer-2")
                .build();

        assertEquals(Boolean.FALSE, ticket.getSlaBreached());
        assertEquals(0L, ticket.getSlaElapsedMs());
    }
}