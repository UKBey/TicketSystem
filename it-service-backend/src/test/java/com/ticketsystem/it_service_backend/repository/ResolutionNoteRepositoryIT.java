package com.ticketsystem.it_service_backend.repository;

import com.ticketsystem.it_service_backend.entity.ResolutionNote;
import com.ticketsystem.it_service_backend.entity.Product;
import com.ticketsystem.it_service_backend.entity.Ticket;
import com.ticketsystem.it_service_backend.entity.TicketTopic;
import com.ticketsystem.it_service_backend.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Transactional
class ResolutionNoteRepositoryIT extends RepositoryIntegrationTestBase {

    @Autowired
    private ResolutionNoteRepository resolutionNoteRepository;

    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private TicketTopicRepository ticketTopicRepository;

    @Autowired
    private UserRepository userRepository;

    private Long ticketId1;
    private Long ticketId2;
    private Long ticketId3;

    @BeforeEach
    void setUp() {
        resolutionNoteRepository.deleteAll();
        ticketRepository.deleteAll();
        ticketTopicRepository.deleteAll();
        userRepository.deleteAll();
        productRepository.deleteAll();

        Product product = productRepository.save(Product.builder()
            .name("Support")
            .isActive(true)
            .build());

        TicketTopic topic = ticketTopicRepository.save(TicketTopic.builder()
            .productId(product.getId()).name("Diğer").isActive(true).build());

        userRepository.save(User.builder().id("customer-1").email("customer-1@test.com").fullName("Customer One").role("CUSTOMER").build());
        userRepository.save(User.builder().id("agent-1").email("agent-1@test.com").fullName("Agent One").role("AGENT").build());
        userRepository.save(User.builder().id("agent-2").email("agent-2@test.com").fullName("Agent Two").role("AGENT").build());

        ticketId1 = ticketRepository.save(Ticket.builder()
            .title("Ticket 1")
            .description("desc")
            .status("IN_PROGRESS")
            .priority("HIGH")
            .productId(product.getId())
            .topicId(topic.getId())
            .customerId("customer-1")
            .build()).getId();

        ticketId2 = ticketRepository.save(Ticket.builder()
            .title("Ticket 2")
            .description("desc")
            .status("IN_PROGRESS")
            .priority("MEDIUM")
            .productId(product.getId())
            .topicId(topic.getId())
            .customerId("customer-1")
            .build()).getId();

        ticketId3 = ticketRepository.save(Ticket.builder()
            .title("Ticket 3")
            .description("desc")
            .status("IN_PROGRESS")
            .priority("LOW")
            .productId(product.getId())
            .topicId(topic.getId())
            .customerId("customer-1")
            .build()).getId();

        resolutionNoteRepository.save(ResolutionNote.builder().ticketId(ticketId1).agentId("agent-1").note("fixed issue").build());
        resolutionNoteRepository.save(ResolutionNote.builder().ticketId(ticketId2).agentId("agent-1").note("another fix").build());
        resolutionNoteRepository.save(ResolutionNote.builder().ticketId(ticketId3).agentId("agent-2").note("agent2 fix").build());
    }

    @Test
    void existsAndFindByTicketId_workAsExpected() {
        assertTrue(resolutionNoteRepository.existsByTicketId(ticketId1));
        assertEquals("fixed issue", resolutionNoteRepository.findByTicketId(ticketId1).orElseThrow().getNote());
    }

    @Test
    void findAllByAgentId_returnsOnlyAgentNotes() {
        List<ResolutionNote> notes = resolutionNoteRepository.findAllByAgentId("agent-1");

        assertEquals(2, notes.size());
    }

    @Test
    void deleteByTicketId_removesMatchingNote() {
        resolutionNoteRepository.deleteByTicketId(ticketId3);

        assertEquals(false, resolutionNoteRepository.existsByTicketId(ticketId3));
    }
}
