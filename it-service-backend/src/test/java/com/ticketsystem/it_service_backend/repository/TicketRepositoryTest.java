package com.ticketsystem.it_service_backend.repository;

import com.ticketsystem.it_service_backend.entity.Ticket;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringJUnitConfig(RepositoryTestConfig.class)
@Transactional
class TicketRepositoryTest {

    @Autowired
    private TicketRepository ticketRepository;

    @BeforeEach
    void setUp() {
        ticketRepository.deleteAll();

        ticketRepository.save(Ticket.builder()
                .title("Customer ticket")
                .description("desc 1")
                .status("NEW")
                .priority("HIGH")
                .productId(10L)
                .customerId("customer-1")
                .assigneeId("agent-1")
                .build());

        ticketRepository.save(Ticket.builder()
                .title("Pool ticket")
                .description("desc 2")
                .status("NEW")
                .priority("LOW")
                .productId(20L)
                .customerId("customer-2")
                .assigneeId(null)
                .build());

        ticketRepository.save(Ticket.builder()
                .title("In progress")
                .description("desc 3")
                .status("IN_PROGRESS")
                .priority("MEDIUM")
                .productId(10L)
                .customerId("customer-3")
                .assigneeId("agent-1")
                .build());
    }

    @Test
    void findByCustomerId_returnsOnlyOwnedTickets() {
        List<Ticket> result = ticketRepository.findByCustomerId("customer-1");

        assertEquals(1, result.size());
        assertEquals("Customer ticket", result.get(0).getTitle());
    }

    @Test
    void findByAssigneeId_returnsAssignedTickets() {
        List<Ticket> result = ticketRepository.findByAssigneeId("agent-1");

        assertEquals(2, result.size());
        assertTrue(result.stream().anyMatch(t -> "Customer ticket".equals(t.getTitle())));
        assertTrue(result.stream().anyMatch(t -> "In progress".equals(t.getTitle())));
    }

    @Test
    void findByStatusAndProductIdIn_filtersByStatusAndProductScope() {
        List<Ticket> result = ticketRepository.findByStatusAndProductIdIn("NEW", List.of(10L));

        assertEquals(1, result.size());
        assertEquals("Customer ticket", result.get(0).getTitle());
    }

    @Test
    void findByCustomerIdOrProductIdIn_mergesCustomerAndAuthorizedProducts() {
        List<Ticket> result = ticketRepository.findByCustomerIdOrProductIdIn("customer-2", List.of(10L));

        assertEquals(3, result.size());
        assertTrue(result.stream().anyMatch(t -> "Pool ticket".equals(t.getTitle())));
        assertTrue(result.stream().anyMatch(t -> "Customer ticket".equals(t.getTitle())));
        assertTrue(result.stream().anyMatch(t -> "In progress".equals(t.getTitle())));
    }
}
