package com.ticketsystem.it_service_backend.repository;

import com.ticketsystem.it_service_backend.entity.TicketWorklog;
import com.ticketsystem.it_service_backend.entity.Product;
import com.ticketsystem.it_service_backend.entity.Ticket;
import com.ticketsystem.it_service_backend.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Transactional
class WorklogRepositoryIT extends RepositoryIntegrationTestBase {

    @Autowired
    private WorklogRepository worklogRepository;

    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private UserRepository userRepository;

    private Long ticketId1;
    private Long ticketId2;

    @BeforeEach
    void setUp() {
        worklogRepository.deleteAll();
        ticketRepository.deleteAll();
        userRepository.deleteAll();
        productRepository.deleteAll();

        Product product = productRepository.save(Product.builder().name("Support").isActive(true).build());

        userRepository.save(User.builder().id("customer-1").email("customer-1@test.com").fullName("Customer One").role("CUSTOMER").build());
        userRepository.save(User.builder().id("agent-1").email("agent-1@test.com").fullName("Agent One").role("AGENT").build());
        userRepository.save(User.builder().id("agent-2").email("agent-2@test.com").fullName("Agent Two").role("AGENT").build());

        ticketId1 = ticketRepository.save(Ticket.builder()
            .title("Ticket 1")
            .description("desc")
            .status("IN_PROGRESS")
            .priority("HIGH")
            .productId(product.getId())
            .customerId("customer-1")
            .build()).getId();

        ticketId2 = ticketRepository.save(Ticket.builder()
            .title("Ticket 2")
            .description("desc")
            .status("IN_PROGRESS")
            .priority("MEDIUM")
            .productId(product.getId())
            .customerId("customer-1")
            .build()).getId();

        worklogRepository.save(TicketWorklog.builder().ticketId(ticketId1).agentId("agent-1").minutes(30).description("investigate").build());
        worklogRepository.save(TicketWorklog.builder().ticketId(ticketId1).agentId("agent-1").minutes(15).description("fix").build());
        worklogRepository.save(TicketWorklog.builder().ticketId(ticketId2).agentId("agent-2").minutes(40).description("analysis").build());
    }

    @Test
    void findByTicketId_returnsTicketWorklogs() {
        List<TicketWorklog> worklogs = worklogRepository.findByTicketId(ticketId1);

        assertEquals(2, worklogs.size());
    }

    @Test
    void findByAgentId_returnsAgentWorklogs() {
        List<TicketWorklog> worklogs = worklogRepository.findByAgentId("agent-1");

        assertEquals(2, worklogs.size());
    }

    @Test
    void deleteByTicketId_removesMatchingRows() {
        worklogRepository.deleteByTicketId(ticketId2);

        assertEquals(0, worklogRepository.findByTicketId(ticketId2).size());
    }
}
