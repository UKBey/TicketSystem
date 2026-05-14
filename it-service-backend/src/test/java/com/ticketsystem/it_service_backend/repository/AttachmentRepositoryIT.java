package com.ticketsystem.it_service_backend.repository;

import com.ticketsystem.it_service_backend.entity.Attachment;
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

@Transactional
class AttachmentRepositoryIT extends RepositoryIntegrationTestBase {

    @Autowired
    private AttachmentRepository attachmentRepository;

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
        attachmentRepository.deleteAll();
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
        userRepository.save(User.builder().id("u1").email("u1@test.com").fullName("User One").role("AGENT").build());
        userRepository.save(User.builder().id("u2").email("u2@test.com").fullName("User Two").role("AGENT").build());

        Ticket ticket = ticketRepository.save(Ticket.builder()
                .title("Attachment ticket")
                .description("desc")
                .status("NEW")
                .priority("LOW")
            .productId(product.getId())
                .topicId(topic.getId())
                .customerId("customer-1")
                .build());
        ticketId = ticket.getId();

        attachmentRepository.save(Attachment.builder().ticket(ticket).uploaderId("u1").fileName("a.log").fileType("text/plain").content("x".getBytes()).build());
        attachmentRepository.save(Attachment.builder().ticket(ticket).uploaderId("u2").fileName("b.log").fileType("text/plain").content("y".getBytes()).build());
    }

    @Test
    void findByTicketId_returnsAllAttachmentsForTicket() {
        List<Attachment> attachments = attachmentRepository.findByTicketId(ticketId);

        assertEquals(2, attachments.size());
    }

    @Test
    void deleteByTicketId_removesAllAttachmentsForTicket() {
        attachmentRepository.deleteByTicketId(ticketId);

        assertEquals(0, attachmentRepository.findByTicketId(ticketId).size());
    }
}
