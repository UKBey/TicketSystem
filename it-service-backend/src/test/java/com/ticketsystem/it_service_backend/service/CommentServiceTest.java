package com.ticketsystem.it_service_backend.service;

import com.ticketsystem.it_service_backend.entity.Comment;
import com.ticketsystem.it_service_backend.entity.Ticket;
import com.ticketsystem.it_service_backend.repository.CommentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CommentServiceTest {

    @Mock
    private CommentRepository commentRepository;
    @Mock
    private TicketService ticketService;

    @InjectMocks
    private CommentService commentService;

    private Ticket waitingTicket;

    @BeforeEach
    void setUp() {
        waitingTicket = Ticket.builder()
                .id(100L)
                .status("WAITING_FOR_CUSTOMER")
                .customerId("customer-1")
                .build();
    }

    @Test
    void addComment_customerCannotAddInternalComment() {
        when(ticketService.validateMutationAccess(100L, "customer-1", List.of("CUSTOMER"))).thenReturn(waitingTicket);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> commentService.addComment(100L, "note", "INTERNAL", "customer-1", List.of("CUSTOMER")));

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
        verify(commentRepository, never()).save(any(Comment.class));
    }

    @Test
    void addComment_customerReplyOnWaitingStatusMovesTicketToInProgress() {
        when(ticketService.validateMutationAccess(100L, "customer-1", List.of("CUSTOMER"))).thenReturn(waitingTicket);
        when(commentRepository.save(any(Comment.class))).thenAnswer(invocation -> {
            Comment c = invocation.getArgument(0);
            c.setId(1L);
            return c;
        });

        Comment saved = commentService.addComment(100L, "I replied", "EXTERNAL", "customer-1", List.of("CUSTOMER"));

        assertEquals(1L, saved.getId());
        verify(ticketService).updateTicketStatus(100L, "IN_PROGRESS", "customer-1", List.of("CUSTOMER"));
    }

    @Test
    void getCommentsByTicketId_customerSeesOnlyExternal() {
        Comment external = Comment.builder().id(1L).type("EXTERNAL").message("public").build();
        Comment internal = Comment.builder().id(2L).type("INTERNAL").message("secret").build();

        when(ticketService.getTicketWithAuth(100L, "customer-1", List.of("CUSTOMER"))).thenReturn(waitingTicket);
        when(commentRepository.findByTicketIdOrderByCreatedAtAsc(100L)).thenReturn(List.of(external, internal));

        List<Comment> result = commentService.getCommentsByTicketId(100L, "customer-1", List.of("CUSTOMER"));

        assertEquals(1, result.size());
        assertEquals("EXTERNAL", result.get(0).getType());
    }

    @Test
    void getCommentsByTicketId_agentSeesAll() {
        Comment external = Comment.builder().id(1L).type("EXTERNAL").message("public").build();
        Comment internal = Comment.builder().id(2L).type("INTERNAL").message("secret").build();

        when(ticketService.getTicketWithAuth(100L, "agent-1", List.of("AGENT"))).thenReturn(waitingTicket);
        when(commentRepository.findByTicketIdOrderByCreatedAtAsc(100L)).thenReturn(List.of(external, internal));

        List<Comment> result = commentService.getCommentsByTicketId(100L, "agent-1", List.of("AGENT"));

        assertEquals(2, result.size());
    }
}
