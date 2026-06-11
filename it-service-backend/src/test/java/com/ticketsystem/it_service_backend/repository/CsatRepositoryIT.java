package com.ticketsystem.it_service_backend.repository;

import com.ticketsystem.it_service_backend.entity.Csat;
import com.ticketsystem.it_service_backend.entity.Product;
import com.ticketsystem.it_service_backend.entity.Ticket;
import com.ticketsystem.it_service_backend.entity.TicketTopic;
import com.ticketsystem.it_service_backend.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Transactional
class CsatRepositoryIT extends RepositoryIntegrationTestBase {

    @Autowired
    private CsatRepository csatRepository;

    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private TicketTopicRepository ticketTopicRepository;

    @Autowired
    private UserRepository userRepository;

    private Long ticketId;

    @BeforeEach
    void setUp() {
        csatRepository.deleteAll();
        ticketRepository.deleteAll();
        ticketTopicRepository.deleteAll();
        userRepository.deleteAll();
        productRepository.deleteAll();

        Product product = productRepository.save(Product.builder()
            .nameEn("Support")
            .isActive(true)
            .build());

        TicketTopic topic = ticketTopicRepository.save(TicketTopic.builder()
            .productId(product.getId()).nameTr("Diğer").isActive(true).build());

        userRepository.save(User.builder().id("customer-1").email("customer-1@test.com").fullName("Customer One").role("CUSTOMER").build());

        Ticket ticket = ticketRepository.save(Ticket.builder()
            .title("CSAT ticket")
            .description("desc")
            .status("RESOLVED")
            .priority("HIGH")
            .productId(product.getId())
            .topicId(topic.getId())
            .customerId("customer-1")
            .build());

        ticketId = ticket.getId();

        csatRepository.save(Csat.builder()
            .ticketId(ticketId)
                .rating(5)
                .comment("Excellent")
                .build());
    }

    @Test
    void existsByTicketId_returnsTrueForExistingTicket() {
        assertTrue(csatRepository.existsByTicketId(ticketId));
        assertFalse(csatRepository.existsByTicketId(999L));
    }

    @Test
    void findByTicketId_returnsCsatRecord() {
        Optional<Csat> result = csatRepository.findByTicketId(ticketId);

        assertTrue(result.isPresent());
        assertEquals(5, result.get().getRating());
        assertEquals("Excellent", result.get().getComment());
    }

    @Test
    void deleteByTicketId_removesMatchingRecord() {
        csatRepository.deleteByTicketId(ticketId);

        assertFalse(csatRepository.existsByTicketId(ticketId));
    }
}
