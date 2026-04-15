package com.ticketsystem.it_service_backend.repository;

import com.ticketsystem.it_service_backend.entity.Attachment;
import com.ticketsystem.it_service_backend.entity.Ticket;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringJUnitConfig(RepositoryTestConfig.class)
@Transactional
class AttachmentRepositoryTest {

    @Autowired
    private AttachmentRepository attachmentRepository;

    @Autowired
    private TicketRepository ticketRepository;

    private Long ticketId;

    @BeforeEach
    void setUp() {
        attachmentRepository.deleteAll();
        ticketRepository.deleteAll();

        Ticket ticket = ticketRepository.save(Ticket.builder()
                .title("Attachment ticket")
                .description("desc")
                .status("NEW")
                .priority("LOW")
                .productId(10L)
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
