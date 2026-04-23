package com.ticketsystem.it_service_backend.repository;

import com.ticketsystem.it_service_backend.entity.Ticket;
import com.ticketsystem.it_service_backend.entity.Product;
import com.ticketsystem.it_service_backend.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Transactional
class TicketRepositoryIT extends RepositoryIntegrationTestBase {

    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private UserRepository userRepository;

    private Long productId1;
    private Long productId2;

    @BeforeEach
    void setUp() {
        ticketRepository.deleteAll();
        userRepository.deleteAll();
        productRepository.deleteAll();

        Product p1 = productRepository.save(Product.builder().name("Support").isActive(true).build());
        Product p2 = productRepository.save(Product.builder().name("Network").isActive(true).build());
        productId1 = p1.getId();
        productId2 = p2.getId();

        userRepository.save(User.builder().id("customer-1").email("customer-1@test.com").fullName("Customer One").role("CUSTOMER").build());
        userRepository.save(User.builder().id("customer-2").email("customer-2@test.com").fullName("Customer Two").role("CUSTOMER").build());
        userRepository.save(User.builder().id("customer-3").email("customer-3@test.com").fullName("Customer Three").role("CUSTOMER").build());
        userRepository.save(User.builder().id("agent-1").email("agent-1@test.com").fullName("Agent One").role("AGENT").build());

        ticketRepository.save(Ticket.builder()
                .title("Customer ticket")
                .description("desc 1")
                .status("NEW")
                .priority("HIGH")
            .productId(productId1)
                .customerId("customer-1")
                .assigneeId("agent-1")
                .build());

        ticketRepository.save(Ticket.builder()
                .title("Pool ticket")
                .description("desc 2")
                .status("NEW")
                .priority("LOW")
                .productId(productId2)
                .customerId("customer-2")
                .assigneeId(null)
                .build());

        ticketRepository.save(Ticket.builder()
                .title("In progress")
                .description("desc 3")
                .status("IN_PROGRESS")
                .priority("MEDIUM")
                .productId(productId1)
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
        List<Ticket> result = ticketRepository.findByStatusAndProductIdIn("NEW", List.of(productId1));

        assertEquals(1, result.size());
        assertEquals("Customer ticket", result.get(0).getTitle());
    }

    @Test
    void findByCustomerIdOrProductIdIn_mergesCustomerAndAuthorizedProducts() {
        List<Ticket> result = ticketRepository.findByCustomerIdOrProductIdIn("customer-2", List.of(productId1));

        assertEquals(3, result.size());
        assertTrue(result.stream().anyMatch(t -> "Pool ticket".equals(t.getTitle())));
        assertTrue(result.stream().anyMatch(t -> "Customer ticket".equals(t.getTitle())));
        assertTrue(result.stream().anyMatch(t -> "In progress".equals(t.getTitle())));
    }
}
