package com.ticketsystem.it_service_backend.repository;

import com.ticketsystem.it_service_backend.entity.Comment;
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
class CommentRepositoryTest {

    @Autowired
    private CommentRepository commentRepository;

    @Autowired
    private TicketRepository ticketRepository;

    private Long ticketId;

    @BeforeEach
    void setUp() {
        commentRepository.deleteAll();
        ticketRepository.deleteAll();

        Ticket ticket = ticketRepository.save(Ticket.builder()
                .title("Ticket")
                .description("desc")
                .status("NEW")
                .priority("HIGH")
                .productId(10L)
                .customerId("customer-1")
                .build());

        ticketId = ticket.getId();

        commentRepository.save(Comment.builder().ticket(ticket).authorId("u1").message("first").type("EXTERNAL").build());
        commentRepository.save(Comment.builder().ticket(ticket).authorId("u2").message("second").type("INTERNAL").build());
    }

    @Test
    void findByTicketIdOrderByCreatedAtAsc_returnsAllComments() {
        List<Comment> comments = commentRepository.findByTicketIdOrderByCreatedAtAsc(ticketId);

        assertEquals(2, comments.size());
    }

    @Test
    void deleteByTicketId_removesCommentsForTicket() {
        commentRepository.deleteByTicketId(ticketId);

        List<Comment> comments = commentRepository.findByTicketIdOrderByCreatedAtAsc(ticketId);
        assertEquals(0, comments.size());
    }
}
