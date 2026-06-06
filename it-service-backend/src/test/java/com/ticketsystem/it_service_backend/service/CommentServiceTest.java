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
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

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
        // @Value field'lari InjectMocks ile null/0 kaliyor; varsayilan production
        // degerleriyle hizalanmis test fixtures.
        ReflectionTestUtils.setField(commentService, "cooldownSeconds", 3L);
        ReflectionTestUtils.setField(commentService, "maxMessageLength", 500);

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
        verify(ticketService).updateTicketStatus(100L, "IN_PROGRESS", null, null, "customer-1", List.of("CUSTOMER"));
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
        verify(ticketService, never()).updateTicketStatus(102L, "IN_PROGRESS", null, null, "agent-1", List.of("AGENT"));
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
        verify(ticketService, never()).updateTicketStatus(any(), any(), any(), any(), any(), any());
    }

    @Test
    void addComment_whenMessageExceeds500Chars_throwsBadRequest() {
        String longMessage = IntStream.range(0, 501)
                .mapToObj(i -> "a")
                .collect(Collectors.joining());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> commentService.addComment(100L, longMessage, "EXTERNAL", "customer-1", List.of("CUSTOMER")));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        verify(commentRepository, never()).save(any(Comment.class));
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

    @Test
    void addComment_customerWithInternalType_throwsForbidden() {
        com.ticketsystem.it_service_backend.entity.Ticket ticket =
                com.ticketsystem.it_service_backend.entity.Ticket.builder().id(110L).status("IN_PROGRESS").customerId("customer-1").build();
        when(ticketService.validateMutationAccess(110L, "customer-1", List.of("CUSTOMER"))).thenReturn(ticket);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> commentService.addComment(110L, "spy", "INTERNAL", "customer-1", List.of("CUSTOMER")));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
        verify(commentRepository, never()).save(any(Comment.class));
    }

    @Test
    void addComment_typeNull_defaultsToExternalAndCheckedAgainstWaitingStatusForCustomer() {
        com.ticketsystem.it_service_backend.entity.Ticket ticket =
                com.ticketsystem.it_service_backend.entity.Ticket.builder().id(120L).status("WAITING_FOR_CUSTOMER").customerId("customer-1").build();
        when(ticketService.validateMutationAccess(120L, "customer-1", List.of("CUSTOMER"))).thenReturn(ticket);
        when(commentRepository.save(any(Comment.class))).thenAnswer(i -> {
            Comment c = i.getArgument(0); c.setId(11L); return c;
        });

        Comment saved = commentService.addComment(120L, "answer", null, "customer-1", List.of("CUSTOMER"));

        assertEquals("EXTERNAL", saved.getType());
        verify(ticketService).updateTicketStatus(120L, "IN_PROGRESS", null, null, "customer-1", List.of("CUSTOMER"));
    }

    @Test
    void getCommentsByTicketId_agentAdminSeesAll() {
        Comment external = Comment.builder().id(1L).type("EXTERNAL").message("public").build();
        Comment internal = Comment.builder().id(2L).type("INTERNAL").message("secret").build();
        when(ticketService.getTicketWithAuth(100L, "admin-1", List.of("ADMIN"))).thenReturn(waitingTicket);
        when(commentRepository.findByTicketIdOrderByCreatedAtAsc(100L)).thenReturn(List.of(external, internal));

        List<Comment> result = commentService.getCommentsByTicketId(100L, "admin-1", List.of("ADMIN"));

        assertEquals(2, result.size());
    }

    // ---- addComment / broadcastComment ek dallar ----

    @Test
    void addComment_messageTooLong_throwsBadRequest() {
        String longMsg = "x".repeat(501);
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> commentService.addComment(100L, longMsg, "EXTERNAL", "agent-1", List.of("AGENT")));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        verify(ticketService, never()).validateMutationAccess(any(), any(), any());
    }

    @Test
    void addComment_withinCooldown_throws429() {
        java.util.Map<String, java.time.Instant> last = new java.util.concurrent.ConcurrentHashMap<>();
        last.put("agent-1", java.time.Instant.now());
        ReflectionTestUtils.setField(commentService, "lastCommentTime", last);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> commentService.addComment(100L, "hi", "EXTERNAL", "agent-1", List.of("AGENT")));
        assertEquals(429, ex.getStatusCode().value());
    }

    @Test
    void addComment_customerReplyOnWaiting_defaultsTypeAndResumesTicket() {
        when(ticketService.validateMutationAccess(100L, "customer-1", List.of("CUSTOMER"))).thenReturn(waitingTicket);
        when(commentRepository.save(any(Comment.class))).thenAnswer(inv -> {
            Comment c = inv.getArgument(0); c.setId(5L); return c;
        });
        when(userRepository.findById("customer-1")).thenReturn(java.util.Optional.empty()); // broadcast author null branch

        Comment saved = commentService.addComment(100L, "cevap", null, "customer-1", List.of("CUSTOMER"));

        assertEquals("EXTERNAL", saved.getType()); // type null → EXTERNAL default
        verify(ticketService).updateTicketStatus(100L, "IN_PROGRESS", null, null, "customer-1", List.of("CUSTOMER"));
        verify(messagingTemplate).convertAndSend(eqDest("/topic/tickets/100"), any(Object.class));
    }

    @Test
    void addComment_agentInternal_broadcastsToInternalDestination() {
        Ticket active = Ticket.builder().id(100L).status("IN_PROGRESS").customerId("customer-1").build();
        when(ticketService.validateMutationAccess(100L, "agent-1", List.of("AGENT"))).thenReturn(active);
        when(commentRepository.save(any(Comment.class))).thenAnswer(inv -> {
            Comment c = inv.getArgument(0); c.setId(6L); return c;
        });
        when(userRepository.findById("agent-1")).thenReturn(java.util.Optional.of(
                com.ticketsystem.it_service_backend.entity.User.builder().id("agent-1").fullName("Ajan").role("AGENT").build()));

        commentService.addComment(100L, "iç not", "INTERNAL", "agent-1", List.of("AGENT"));

        verify(messagingTemplate).convertAndSend(eqDest("/topic/tickets/100/internal"), any(Object.class));
        verify(ticketService, never()).updateTicketStatus(any(), any(), any(), any(), any(), any());
    }

    private static String eqDest(String d) { return org.mockito.ArgumentMatchers.eq(d); }
}
