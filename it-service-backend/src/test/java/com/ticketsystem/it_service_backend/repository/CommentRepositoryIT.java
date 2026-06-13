package com.ticketsystem.it_service_backend.repository;

import com.ticketsystem.it_service_backend.entity.Comment;
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
import com.ticketsystem.it_service_backend.entity.TicketStatus;
import com.ticketsystem.it_service_backend.entity.Priority;
import com.ticketsystem.it_service_backend.entity.CommentType;

@Transactional
class CommentRepositoryIT extends RepositoryIntegrationTestBase {

    @Autowired
    private CommentRepository commentRepository;

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
        commentRepository.deleteAll();
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
        userRepository.save(User.builder().id("u1").email("u1@test.com").fullName("User One").role("AGENT").build());
        userRepository.save(User.builder().id("u2").email("u2@test.com").fullName("User Two").role("AGENT").build());

        Ticket ticket = ticketRepository.save(Ticket.builder()
                .title("Ticket")
                .description("desc")
                .status(TicketStatus.NEW)
                .priority(Priority.HIGH)
            .productId(product.getId())
                .topicId(topic.getId())
                .customerId("customer-1")
                .build());

        ticketId = ticket.getId();

        commentRepository.save(Comment.builder().ticket(ticket).authorId("u1").message("first").type(CommentType.EXTERNAL).build());
        commentRepository.save(Comment.builder().ticket(ticket).authorId("u2").message("second").type(CommentType.INTERNAL).build());
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
