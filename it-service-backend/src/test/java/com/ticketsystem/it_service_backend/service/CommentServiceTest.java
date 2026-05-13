package com.ticketsystem.it_service_backend.service;

import com.ticketsystem.it_service_backend.entity.Comment;
import com.ticketsystem.it_service_backend.entity.Ticket;
import com.ticketsystem.it_service_backend.repository.CommentRepository;
import com.ticketsystem.it_service_backend.repository.UserRepository;
import org.springframework.messaging.simp.SimpMessagingTemplate;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CommentServiceTest {

    @Mock
    private CommentRepository commentRepository;
    @Mock
    private TicketService ticketService;
    @Mock
    private NotificationService notificationService;
    @Mock
    private UserRepository userRepository;
    @Mock
    private SimpMessagingTemplate messagingTemplate;

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

    @Test
    void addComment_whenTypeMissing_defaultsToExternal() {
        Ticket inProgressTicket = Ticket.builder()
                .id(101L)
                .status("IN_PROGRESS")
                .customerId("customer-1")
                .build();

        when(ticketService.validateMutationAccess(101L, "agent-1", List.of("AGENT"))).thenReturn(inProgressTicket);
        when(commentRepository.save(any(Comment.class))).thenAnswer(invocation -> {
            Comment c = invocation.getArgument(0);
            c.setId(7L);
            return c;
        });

        Comment saved = commentService.addComment(101L, "agent note", null, "agent-1", List.of("AGENT"));

        assertEquals(7L, saved.getId());
        assertEquals("EXTERNAL", saved.getType());
    }

    @Test
    void addComment_whenAgentAddsInternalComment_isAllowed() {
        Ticket inProgressTicket = Ticket.builder()
                .id(102L)
                .status("IN_PROGRESS")
                .customerId("customer-1")
                .build();

        when(ticketService.validateMutationAccess(102L, "agent-1", List.of("AGENT"))).thenReturn(inProgressTicket);
        when(commentRepository.save(any(Comment.class))).thenAnswer(invocation -> {
            Comment c = invocation.getArgument(0);
            c.setId(8L);
            return c;
        });

        Comment saved = commentService.addComment(102L, "internal", "INTERNAL", "agent-1", List.of("AGENT"));

        assertEquals(8L, saved.getId());
        assertEquals("INTERNAL", saved.getType());
        verify(ticketService, never()).updateTicketStatus(102L, "IN_PROGRESS", "agent-1", List.of("AGENT"));
    }

    @Test
    void addComment_whenWaitingStatusButNotCustomer_doesNotUpdateTicketStatus() {
        Ticket waiting = Ticket.builder()
                .id(103L)
                .status("WAITING_FOR_CUSTOMER")
                .customerId("customer-1")
                .build();

        when(ticketService.validateMutationAccess(103L, "agent-1", List.of("AGENT"))).thenReturn(waiting);
        when(commentRepository.save(any(Comment.class))).thenAnswer(invocation -> {
            Comment c = invocation.getArgument(0);
            c.setId(9L);
            return c;
        });

        Comment saved = commentService.addComment(103L, "ping", "EXTERNAL", "agent-1", List.of("AGENT"));

        assertNotNull(saved);
        verify(ticketService, never()).updateTicketStatus(any(), any(), any(), any());
    }

    @Test
    void getCommentsByTicketId_customerWithAgentRoleSeesAll() {
        Comment external = Comment.builder().id(1L).type("EXTERNAL").message("public").build();
        Comment internal = Comment.builder().id(2L).type("INTERNAL").message("secret").build();

        when(ticketService.getTicketWithAuth(100L, "agent-customer", List.of("CUSTOMER", "AGENT"))).thenReturn(waitingTicket);
        when(commentRepository.findByTicketIdOrderByCreatedAtAsc(100L)).thenReturn(List.of(external, internal));

        List<Comment> result = commentService.getCommentsByTicketId(100L, "agent-customer", List.of("CUSTOMER", "AGENT"));

        assertEquals(2, result.size());
        verify(commentRepository, times(1)).findByTicketIdOrderByCreatedAtAsc(100L);
    }
}
