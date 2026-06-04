package com.ticketsystem.it_service_backend.repository;

import com.ticketsystem.it_service_backend.entity.Csat;
import com.ticketsystem.it_service_backend.entity.Ticket;
import com.ticketsystem.it_service_backend.entity.Product;
import com.ticketsystem.it_service_backend.entity.TicketTopic;
import com.ticketsystem.it_service_backend.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
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
    private TicketTopicRepository ticketTopicRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CsatRepository csatRepository;

    private Long productId1;
    private Long productId2;
    private Long topicId1;
    private Long topicId2;

    @BeforeEach
    void setUp() {
        ticketRepository.deleteAll();
        ticketTopicRepository.deleteAll();
        userRepository.deleteAll();
        productRepository.deleteAll();

        Product p1 = productRepository.save(Product.builder().name("Support").isActive(true).build());
        Product p2 = productRepository.save(Product.builder().name("Network").isActive(true).build());
        productId1 = p1.getId();
        productId2 = p2.getId();
        topicId1 = ticketTopicRepository.save(TicketTopic.builder().productId(productId1).name("Diğer").isActive(true).build()).getId();
        topicId2 = ticketTopicRepository.save(TicketTopic.builder().productId(productId2).name("Diğer").isActive(true).build()).getId();

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
                .topicId(topicId1)
                .customerId("customer-1")
                .build());

        ticketRepository.save(Ticket.builder()
                .title("Pool ticket")
                .description("desc 2")
                .status("NEW")
                .priority("LOW")
                .productId(productId2)
                .topicId(topicId2)
                .customerId("customer-2")
                .build());

        ticketRepository.save(Ticket.builder()
                .title("In progress")
                .description("desc 3")
                .status("IN_PROGRESS")
                .priority("MEDIUM")
                .productId(productId1)
                .topicId(topicId1)
                .customerId("customer-3")
                .build());
    }

    @Test
    void findByCustomerId_returnsOnlyOwnedTickets() {
        List<Ticket> result = ticketRepository.findByCustomerId("customer-1");

        assertEquals(1, result.size());
        assertEquals("Customer ticket", result.get(0).getTitle());
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

    // -------------------------------------------------------------------------
    // Full-filtered native queries + CSAT filter/sort (regression: native ORDER BY
    // must auto-qualify to the single root alias `t` without ambiguity).
    // -------------------------------------------------------------------------

    private static final List<String> ALL_STATUSES =
            List.of("NEW", "IN_PROGRESS", "WAITING_FOR_CUSTOMER", "RESOLVED", "CLOSED");
    private static final List<String> ALL_PRIORITIES = List.of("CRITICAL", "HIGH", "MEDIUM", "LOW");
    private static final List<String> ALL_SLA = List.of("BREACHED", "ACTIVE", "PAUSED");

    private Long ticketId(String customerId) {
        return ticketRepository.findByCustomerId(customerId).get(0).getId();
    }

    /** Regression for the `t.t.created_at` double-qualification bug: default sort must work. */
    @Test
    void findTeamTicketsFullFiltered_defaultSort_noCsatFilter_returnsScopedTickets() {
        Page<Ticket> result = ticketRepository.findTeamTicketsFullFiltered(
                List.of(productId1, productId2), ALL_STATUSES, ALL_PRIORITIES,
                List.of(productId1, productId2), null, ALL_SLA,
                false, List.of("none"), false, List.of(-1L), null, null,
                false, List.of(-1), false,
                PageRequest.of(0, 20, Sort.by(Sort.Order.desc("created_at"))));

        assertEquals(3, result.getTotalElements());
    }

    @Test
    void findTeamTicketsFullFiltered_csatFilter_matchesSelectedRatingsAndNone() {
        Long tA = ticketId("customer-1"); // product1
        Long tC = ticketId("customer-3"); // product1
        csatRepository.save(Csat.builder().ticketId(tA).rating(5).comment("great").build());
        csatRepository.save(Csat.builder().ticketId(tC).rating(2).comment("meh").build());

        Page<Ticket> rated5 = ticketRepository.findTeamTicketsFullFiltered(
                List.of(productId1, productId2), ALL_STATUSES, ALL_PRIORITIES,
                List.of(productId1, productId2), null, ALL_SLA,
                false, List.of("none"), false, List.of(-1L), null, null,
                true, List.of(5), false, PageRequest.of(0, 20));
        assertEquals(1, rated5.getTotalElements());
        assertEquals(tA, rated5.getContent().get(0).getId());

        // NONE bucket: only the ticket without a survey (customer-2's Pool ticket).
        Page<Ticket> none = ticketRepository.findTeamTicketsFullFiltered(
                List.of(productId1, productId2), ALL_STATUSES, ALL_PRIORITIES,
                List.of(productId1, productId2), null, ALL_SLA,
                false, List.of("none"), false, List.of(-1L), null, null,
                true, List.of(-1), true, PageRequest.of(0, 20));
        assertEquals(1, none.getTotalElements());
        assertEquals(ticketId("customer-2"), none.getContent().get(0).getId());
    }

    @Test
    void findTeamTicketsFullFilteredOrderByCsat_sortsByRatingNullsLast() {
        Long tA = ticketId("customer-1"); // rating 5
        Long tB = ticketId("customer-2"); // no survey
        Long tC = ticketId("customer-3"); // rating 2
        csatRepository.save(Csat.builder().ticketId(tA).rating(5).build());
        csatRepository.save(Csat.builder().ticketId(tC).rating(2).build());

        Page<Ticket> asc = ticketRepository.findTeamTicketsFullFilteredOrderByCsatAsc(
                List.of(productId1, productId2), ALL_STATUSES, ALL_PRIORITIES,
                List.of(productId1, productId2), null, ALL_SLA,
                false, List.of("none"), false, List.of(-1L), null, null,
                false, List.of(-1), false, PageRequest.of(0, 20));

        assertEquals(3, asc.getTotalElements());
        List<Ticket> rows = asc.getContent();
        assertEquals(tC, rows.get(0).getId()); // 2
        assertEquals(tA, rows.get(1).getId()); // 5
        assertEquals(tB, rows.get(2).getId()); // null → last
    }
}
