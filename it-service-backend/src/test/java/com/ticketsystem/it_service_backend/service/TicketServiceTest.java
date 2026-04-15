package com.ticketsystem.it_service_backend.service;

import com.ticketsystem.it_service_backend.entity.Comment;
import com.ticketsystem.it_service_backend.entity.Product;
import com.ticketsystem.it_service_backend.entity.Ticket;
import com.ticketsystem.it_service_backend.entity.User;
import com.ticketsystem.it_service_backend.event.TicketCreatedEvent;
import com.ticketsystem.it_service_backend.repository.AttachmentRepository;
import com.ticketsystem.it_service_backend.repository.CommentRepository;
import com.ticketsystem.it_service_backend.repository.CsatRepository;
import com.ticketsystem.it_service_backend.repository.ResolutionNoteRepository;
import com.ticketsystem.it_service_backend.repository.TicketRepository;
import com.ticketsystem.it_service_backend.repository.UserRepository;
import com.ticketsystem.it_service_backend.repository.WorklogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TicketServiceTest {

    @Mock
    private TicketRepository ticketRepository;
    @Mock
    private CommentRepository commentRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private WorkflowService workflowService;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private CsatRepository csatRepository;
    @Mock
    private ResolutionNoteRepository resolutionNoteRepository;
    @Mock
    private WorklogRepository worklogRepository;
    @Mock
    private AttachmentRepository attachmentRepository;

    @InjectMocks
    private TicketService ticketService;

    private Product product;
    private User customer;
    private User agent;

    @BeforeEach
    void setUp() {
        product = Product.builder().id(10L).name("CRM").build();

        customer = User.builder()
                .id("customer-1")
                .email("customer@example.com")
                .fullName("Customer User")
                .role("CUSTOMER")
                .authorizedProducts(List.of(product))
                .build();

        agent = User.builder()
                .id("agent-1")
                .email("agent@example.com")
                .fullName("Agent User")
                .role("AGENT")
                .authorizedProducts(List.of(product))
                .build();
    }

    @Test
    void createTicket_whenCustomerAuthorized_savesTicketCommentAndPublishesEvent() {
        Ticket input = Ticket.builder()
                .title("Cannot login")
                .description("Login fails with 500")
                .priority("HIGH")
                .productId(10L)
                .build();

        when(userRepository.findById("customer-1")).thenReturn(Optional.of(customer));
        when(ticketRepository.save(any(Ticket.class))).thenAnswer(invocation -> {
            Ticket toSave = invocation.getArgument(0);
            toSave.setId(101L);
            return toSave;
        });

        Ticket saved = ticketService.createTicket(input, "customer-1");

        assertNotNull(saved.getId());
        assertEquals("NEW", saved.getStatus());
        assertEquals("customer-1", saved.getCustomerId());

        ArgumentCaptor<Comment> commentCaptor = ArgumentCaptor.forClass(Comment.class);
        verify(commentRepository, times(1)).save(commentCaptor.capture());
        Comment firstComment = commentCaptor.getValue();
        assertEquals("customer-1", firstComment.getAuthorId());
        assertEquals("Login fails with 500", firstComment.getMessage());
        assertEquals("EXTERNAL", firstComment.getType());
        assertEquals(101L, firstComment.getTicket().getId());

        ArgumentCaptor<TicketCreatedEvent> eventCaptor = ArgumentCaptor.forClass(TicketCreatedEvent.class);
        verify(eventPublisher, times(1)).publishEvent(eventCaptor.capture());
        assertEquals(101L, eventCaptor.getValue().ticket().getId());
    }

    @Test
    void createTicket_whenCustomerNotAuthorized_throwsForbidden() {
        User unauthorizedCustomer = User.builder()
                .id("customer-2")
                .email("unauth@example.com")
                .fullName("Unauthorized Customer")
                .role("CUSTOMER")
                .authorizedProducts(List.of())
                .build();

        Ticket input = Ticket.builder()
                .title("Issue")
                .description("Description")
                .priority("LOW")
                .productId(10L)
                .build();

        when(userRepository.findById("customer-2")).thenReturn(Optional.of(unauthorizedCustomer));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> ticketService.createTicket(input, "customer-2"));

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
        verify(ticketRepository, never()).save(any(Ticket.class));
        verify(commentRepository, never()).save(any(Comment.class));
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void claimTicket_whenTicketNotNew_throwsRuntimeException() {
        Ticket existing = Ticket.builder()
                .id(201L)
                .title("Already in progress")
                .description("desc")
                .priority("MEDIUM")
                .status("IN_PROGRESS")
                .productId(10L)
                .customerId("customer-1")
                .build();

        when(ticketRepository.findById(201L)).thenReturn(Optional.of(existing));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> ticketService.claimTicket(201L, "agent-1"));

        assertEquals("Sadece NEW statüsündeki biletler üzerinize alınabilir.", ex.getMessage());
        verify(ticketRepository, never()).save(any(Ticket.class));
    }

    @Test
    void claimTicket_whenAgentUnauthorizedForProduct_throwsForbidden() {
        Ticket existing = Ticket.builder()
                .id(202L)
                .title("Pool ticket")
                .description("desc")
                .priority("MEDIUM")
                .status("NEW")
                .productId(999L)
                .customerId("customer-1")
                .build();

        User unauthorizedAgent = User.builder()
                .id("agent-2")
                .email("agent2@example.com")
                .fullName("Unauthorized Agent")
                .role("AGENT")
                .authorizedProducts(List.of(product))
                .build();

        when(ticketRepository.findById(202L)).thenReturn(Optional.of(existing));
        when(userRepository.findById("agent-2")).thenReturn(Optional.of(unauthorizedAgent));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> ticketService.claimTicket(202L, "agent-2"));

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
        verify(ticketRepository, never()).save(any(Ticket.class));
    }

    @Test
    void updateTicketStatus_whenInvalidTransition_throwsBadRequest() {
        Ticket existing = Ticket.builder()
                .id(301L)
                .title("Ticket")
                .description("desc")
                .priority("HIGH")
                .status("NEW")
                .productId(10L)
                .customerId("customer-1")
                .build();

        when(ticketRepository.findById(301L)).thenReturn(Optional.of(existing));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> ticketService.updateTicketStatus(301L, "CLOSED", "agent-1", List.of("AGENT")));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        verify(ticketRepository, never()).save(any(Ticket.class));
    }

    @Test
    void updateTicketStatus_whenCustomerMakesForbiddenTransition_throwsForbidden() {
        Ticket existing = Ticket.builder()
                .id(302L)
                .title("Ticket")
                .description("desc")
                .priority("HIGH")
                .status("NEW")
                .productId(10L)
                .customerId("customer-1")
                .build();

        when(ticketRepository.findById(302L)).thenReturn(Optional.of(existing));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> ticketService.updateTicketStatus(302L, "IN_PROGRESS", "customer-1", List.of("CUSTOMER")));

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
        verify(ticketRepository, never()).save(any(Ticket.class));
    }

    @Test
    void deleteTicket_whenWorkflowAbortFails_stillDeletesRelatedDataAndTicket() {
        Ticket existing = Ticket.builder()
                .id(401L)
                .title("Delete me")
                .description("desc")
                .priority("LOW")
                .status("NEW")
                .productId(10L)
                .customerId("customer-1")
                .build();

        when(ticketRepository.findById(401L)).thenReturn(Optional.of(existing));
        doThrow(new RuntimeException("workflow down")).when(workflowService).abortTicketWorkflow(existing);

        ticketService.deleteTicket(401L);

        verify(commentRepository).deleteByTicketId(401L);
        verify(csatRepository).deleteByTicketId(401L);
        verify(resolutionNoteRepository).deleteByTicketId(401L);
        verify(worklogRepository).deleteByTicketId(401L);
        verify(attachmentRepository).deleteByTicketId(401L);
        verify(ticketRepository).deleteById(401L);
    }

    @Test
    void getSlaTimerInfoByTicket_delegatesToWorkflowService() {
        Ticket existing = Ticket.builder().id(501L).build();
        when(workflowService.getSlaTimerInfo(existing)).thenReturn(Map.of("deadlineTs", 12345L));

        Map<String, Long> result = ticketService.getSlaTimerInfo(existing);

        assertEquals(12345L, result.get("deadlineTs"));
        verify(workflowService).getSlaTimerInfo(existing);
    }

    @Test
    void updateTicketStatus_agentAuthorized_canMoveNewToInProgress() {
        Ticket existing = Ticket.builder()
                .id(601L)
                .title("Ticket")
                .description("desc")
                .priority("HIGH")
                .status("NEW")
                .productId(10L)
                .customerId("customer-1")
                .build();

        when(ticketRepository.findById(601L)).thenReturn(Optional.of(existing));
        when(userRepository.findById("agent-1")).thenReturn(Optional.of(agent));
        when(ticketRepository.save(any(Ticket.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Ticket updated = ticketService.updateTicketStatus(601L, "IN_PROGRESS", "agent-1", List.of("AGENT"));

        assertEquals("IN_PROGRESS", updated.getStatus());
        assertNull(updated.getResolvedAt());
        verify(workflowService).syncTicketStatus(updated);
    }
}
