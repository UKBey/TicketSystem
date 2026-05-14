package com.ticketsystem.it_service_backend.service;

import com.ticketsystem.it_service_backend.dto.TicketFilterDTO;
import com.ticketsystem.it_service_backend.entity.AgentProductLimit;
import com.ticketsystem.it_service_backend.entity.Comment;
import com.ticketsystem.it_service_backend.entity.Product;
import com.ticketsystem.it_service_backend.entity.Ticket;
import com.ticketsystem.it_service_backend.entity.TicketClaim;
import com.ticketsystem.it_service_backend.entity.TicketTopic;
import com.ticketsystem.it_service_backend.entity.User;
import com.ticketsystem.it_service_backend.event.TicketCreatedEvent;
import com.ticketsystem.it_service_backend.repository.AttachmentRepository;
import com.ticketsystem.it_service_backend.repository.AgentProductLimitRepository;
import com.ticketsystem.it_service_backend.repository.CommentRepository;
import com.ticketsystem.it_service_backend.repository.CsatRepository;
import com.ticketsystem.it_service_backend.repository.ResolutionNoteRepository;
import com.ticketsystem.it_service_backend.repository.ProductRepository;
import com.ticketsystem.it_service_backend.repository.TicketAuditLogRepository;
import com.ticketsystem.it_service_backend.repository.TicketClaimRepository;
import com.ticketsystem.it_service_backend.repository.TicketRepository;
import com.ticketsystem.it_service_backend.repository.TicketTopicRepository;
import com.ticketsystem.it_service_backend.repository.UserRepository;
import com.ticketsystem.it_service_backend.repository.WorklogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.server.ResponseStatusException;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
        private ProductRepository productRepository;
        @Mock
        private AgentProductLimitRepository agentProductLimitRepository;
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
    @Mock
    private NotificationService notificationService;
    @Mock
    private TicketClaimRepository ticketClaimRepository;
    @Mock
    private TicketAuditLogRepository ticketAuditLogRepository;
    @Mock
    private SlaPolicyService slaPolicyService;
    @Mock
    private SimpMessagingTemplate messagingTemplate;
    @Mock
    private TicketTopicRepository ticketTopicRepository;

    @InjectMocks
    private TicketService ticketService;

    private Product product;
    private TicketTopic topic;
    private User customer;
    private User agent;
    private User agentAdmin;

    @BeforeEach
    void setUp() {
        product = Product.builder().id(10L).name("CRM").build();
        topic = TicketTopic.builder().id(50L).productId(10L).name("Diğer").isActive(true).build();

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

        agentAdmin = User.builder()
                .id("admin-1")
                .email("admin@example.com")
                .fullName("Admin User")
                .role("AGENT_ADMIN")
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
                .topicId(50L)
                .build();

        when(userRepository.findById("customer-1")).thenReturn(Optional.of(customer));
        when(ticketTopicRepository.findById(50L)).thenReturn(Optional.of(topic));
        when(slaPolicyService.getSlaDurationMs("HIGH")).thenReturn(14_400_000L);
        when(ticketRepository.save(any(Ticket.class))).thenAnswer(invocation -> {
            Ticket toSave = invocation.getArgument(0);
            toSave.setId(101L);
            return toSave;
        });

        Ticket saved = ticketService.createTicket(input, "customer-1");

        assertNotNull(saved.getId());
        assertEquals("NEW", saved.getStatus());
        assertEquals("customer-1", saved.getCustomerId());
        assertNotNull(saved.getSlaDeadline(), "slaDeadline bilet oluşturulurken set edilmeli");

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
        void getAllTickets_whenAgentAdmin_returnsAllTickets() {
                Ticket ticket = Ticket.builder().id(700L).build();
                when(ticketRepository.findAll()).thenReturn(List.of(ticket));

                List<Ticket> result = ticketService.getAllTickets("admin-1", List.of("AGENT_ADMIN"));

                assertEquals(1, result.size());
                assertEquals(700L, result.get(0).getId());
        }

        @Test
        void getAllTickets_whenManager_returnsAllTickets() {
                Ticket ticket = Ticket.builder().id(701L).build();
                when(ticketRepository.findAll()).thenReturn(List.of(ticket));

                // Manager no longer has global access; AGENT_ADMIN should be used for this behavior in production.
                List<Ticket> result = ticketService.getAllTickets("admin-1", List.of("AGENT_ADMIN"));

                assertEquals(1, result.size());
                assertEquals(701L, result.get(0).getId());
        }

        @Test
        void getAllTickets_whenUserIdMissing_returnsEmptyList() {
                List<Ticket> result = ticketService.getAllTickets(null, List.of("AGENT"));

                assertEquals(0, result.size());
        }

        @Test
        void getAllTickets_whenAgentReturnsAuthorizedTickets() {
                Ticket ticket = Ticket.builder().id(702L).build();
                User agentUser = User.builder()
                                .id("agent-1")
                                .email("agent@example.com")
                                .fullName("Agent User")
                                .role("AGENT")
                                .authorizedProducts(List.of(product))
                                .build();

                when(userRepository.findById("agent-1")).thenReturn(Optional.of(agentUser));
                when(ticketRepository.findByCustomerIdOrProductIdIn("agent-1", List.of(10L))).thenReturn(List.of(ticket));

                List<Ticket> result = ticketService.getAllTickets("agent-1", List.of("AGENT"));

                assertEquals(1, result.size());
                assertEquals(702L, result.get(0).getId());
        }

        @Test
        void getPoolTickets_whenAgentAdmin_returnsNewTickets() {
                Ticket ticket = Ticket.builder().id(702L).build();
                when(ticketRepository.findByStatus("NEW")).thenReturn(List.of(ticket));

                List<Ticket> result = ticketService.getPoolTickets("admin-1", List.of("AGENT_ADMIN"));

                assertEquals(1, result.size());
                assertEquals(702L, result.get(0).getId());
        }

        @Test
        void getPoolTickets_whenManager_returnsNewTickets() {
                Ticket ticket = Ticket.builder().id(703L).build();
                when(ticketRepository.findByStatus("NEW")).thenReturn(List.of(ticket));

                // Manager no longer has pool access; AGENT_ADMIN should be used for full pool access.
                List<Ticket> result = ticketService.getPoolTickets("admin-1", List.of("AGENT_ADMIN"));

                assertEquals(1, result.size());
                assertEquals(703L, result.get(0).getId());
        }

        @Test
        void getPoolTickets_whenAgentHasNoProducts_returnsEmptyList() {
                User agentWithoutProducts = User.builder()
                                .id("agent-3")
                                .email("agent3@example.com")
                                .fullName("Agent Three")
                                .role("AGENT")
                                .authorizedProducts(List.of())
                                .build();

                when(userRepository.findById("agent-3")).thenReturn(Optional.of(agentWithoutProducts));

                List<Ticket> result = ticketService.getPoolTickets("agent-3", List.of("AGENT"));

                assertEquals(0, result.size());
        }

        @Test
        void getPoolTickets_whenUserIdMissing_returnsEmptyList() {
                List<Ticket> result = ticketService.getPoolTickets(null, List.of("AGENT"));

                assertEquals(0, result.size());
        }

        @Test
        void getPoolTickets_whenAgentHasProducts_returnsMatchingNewTickets() {
                Ticket ticket = Ticket.builder().id(703L).status("NEW").productId(10L).build();
                when(userRepository.findById("agent-1")).thenReturn(Optional.of(agent));
                when(ticketRepository.findByStatusAndProductIdIn("NEW", List.of(10L))).thenReturn(List.of(ticket));

                List<Ticket> result = ticketService.getPoolTickets("agent-1", List.of("AGENT"));

                assertEquals(1, result.size());
                assertEquals(703L, result.get(0).getId());
        }

        @Test
        void getTicketWithAuth_whenAgentAdmin_returnsTicket() {
                Ticket ticket = Ticket.builder().id(705L).customerId("customer-1").productId(10L).build();
                when(ticketRepository.findById(705L)).thenReturn(Optional.of(ticket));
                when(userRepository.findById("admin-1")).thenReturn(Optional.of(agentAdmin));

                Ticket result = ticketService.getTicketWithAuth(705L, "admin-1", List.of("AGENT_ADMIN"));

                assertEquals(705L, result.getId());
        }

        @Test
        void getTicketWithAuth_whenManager_returnsTicket() {
                Ticket ticket = Ticket.builder().id(704L).customerId("customer-1").productId(10L).build();
                when(ticketRepository.findById(704L)).thenReturn(Optional.of(ticket));
                when(userRepository.findById("admin-1")).thenReturn(Optional.of(agentAdmin));

                // Manager no longer automatically sees tickets; AGENT_ADMIN should be used for full access.
                Ticket result = ticketService.getTicketWithAuth(704L, "admin-1", List.of("AGENT_ADMIN"));

                assertEquals(704L, result.getId());
        }

        @Test
        void getTicketWithAuth_whenCustomerOwnsTicket_returnsTicket() {
                Ticket ticket = Ticket.builder().id(705L).customerId("customer-1").build();
                when(ticketRepository.findById(705L)).thenReturn(Optional.of(ticket));

                Ticket result = ticketService.getTicketWithAuth(705L, "customer-1", List.of("CUSTOMER"));

                assertEquals(705L, result.getId());
        }

        @Test
        void getTicketWithAuth_whenAgentAuthorized_returnsTicket() {
                Ticket ticket = Ticket.builder().id(706L).productId(10L).customerId("customer-1").build();
                when(ticketRepository.findById(706L)).thenReturn(Optional.of(ticket));
                when(userRepository.findById("agent-1")).thenReturn(Optional.of(agent));

                Ticket result = ticketService.getTicketWithAuth(706L, "agent-1", List.of("AGENT"));

                assertEquals(706L, result.getId());
        }

        @Test
        void getTicketWithAuth_whenAgentNotAuthorized_throwsForbidden() {
                Ticket ticket = Ticket.builder().id(706L).productId(99L).customerId("customer-1").build();
                when(ticketRepository.findById(706L)).thenReturn(Optional.of(ticket));
                when(userRepository.findById("agent-1")).thenReturn(Optional.of(agent));

                ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                                () -> ticketService.getTicketWithAuth(706L, "agent-1", List.of("AGENT")));

                assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
        }

        @Test
        void validateMutationAccess_whenManager_returnsTicket() {
                Ticket ticket = Ticket.builder().id(707L).customerId("customer-1").productId(10L).build();
                when(ticketRepository.findById(707L)).thenReturn(Optional.of(ticket));
                when(userRepository.findById("admin-1")).thenReturn(Optional.of(agentAdmin));
                when(ticketClaimRepository.existsByTicketIdAndAgentId(707L, "admin-1")).thenReturn(true);

                // Manager role no longer authorizes mutations; AGENT_ADMIN should be used.
                Ticket result = ticketService.validateMutationAccess(707L, "admin-1", List.of("AGENT_ADMIN"));

                assertEquals(707L, result.getId());
        }

        @Test
        void validateMutationAccess_whenCustomerOwnsTicket_returnsTicket() {
                Ticket ticket = Ticket.builder().id(708L).customerId("customer-1").build();
                when(ticketRepository.findById(708L)).thenReturn(Optional.of(ticket));

                Ticket result = ticketService.validateMutationAccess(708L, "customer-1", List.of("CUSTOMER"));

                assertEquals(708L, result.getId());
        }

        @Test
        void validateMutationAccess_whenAgentIsClaimer_returnsTicket() {
                Ticket ticket = Ticket.builder().id(709L).customerId("customer-1").build();
                when(ticketRepository.findById(709L)).thenReturn(Optional.of(ticket));
                when(ticketClaimRepository.existsByTicketIdAndAgentId(709L, "agent-1")).thenReturn(true);

                Ticket result = ticketService.validateMutationAccess(709L, "agent-1", List.of("AGENT"));

                assertEquals(709L, result.getId());
        }

        @Test
        void validateMutationAccess_whenAgentNotClaimer_throwsForbidden() {
                Ticket ticket = Ticket.builder().id(710L).customerId("customer-1").build();
                when(ticketRepository.findById(710L)).thenReturn(Optional.of(ticket));
                when(ticketClaimRepository.existsByTicketIdAndAgentId(710L, "agent-1")).thenReturn(false);

                ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                                () -> ticketService.validateMutationAccess(710L, "agent-1", List.of("AGENT")));

                assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
        }

        @Test
        void validateMutationAccess_whenCustomerNotOwner_throwsForbidden() {
                Ticket ticket = Ticket.builder().id(711L).customerId("customer-2").build();
                when(ticketRepository.findById(711L)).thenReturn(Optional.of(ticket));

                ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                                () -> ticketService.validateMutationAccess(711L, "customer-1", List.of("CUSTOMER")));

                assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
        }

        @Test
        void validateMutationAccess_whenNoRecognizedRole_throwsForbidden() {
                Ticket ticket = Ticket.builder().id(712L).customerId("customer-1").build();
                when(ticketRepository.findById(712L)).thenReturn(Optional.of(ticket));

                ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                                () -> ticketService.validateMutationAccess(712L, "user-1", List.of("VIEWER")));

                assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
        }

        @Test
        void claimTicket_whenAgentAuthorized_updatesAssignmentAndSyncsWorkflow() {
                Ticket existing = Ticket.builder()
                                .id(203L)
                                .title("Pool ticket")
                                .description("desc")
                                .priority("MEDIUM")
                                .status("NEW")
                                .productId(10L)
                                .customerId("customer-1")
                                .build();

                Product limitedProduct = Product.builder().id(10L).name("CRM").maxActiveTickets(5).build();

                when(ticketRepository.findById(203L)).thenReturn(Optional.of(existing));
                when(userRepository.findById("agent-1")).thenReturn(Optional.of(agent));
                when(productRepository.findById(10L)).thenReturn(Optional.of(limitedProduct));
                when(agentProductLimitRepository.findByAgentIdAndProductId("agent-1", 10L)).thenReturn(Optional.empty());
                when(ticketClaimRepository.countActiveTicketsByAgentAndProduct("agent-1", 10L)).thenReturn(0L);
                when(ticketRepository.save(any(Ticket.class))).thenAnswer(invocation -> invocation.getArgument(0));

                Ticket updated = ticketService.claimTicket(203L, "agent-1");

                assertEquals("IN_PROGRESS", updated.getStatus());
                verify(workflowService).syncTicketAssignment(updated, "agent-1");
        }

        @Test
        void claimTicket_whenActiveCountReachesLimit_throwsTicketLimitExceeded() {
                Ticket existing = Ticket.builder()
                                .id(204L)
                                .title("Limit ticket")
                                .description("desc")
                                .priority("MEDIUM")
                                .status("NEW")
                                .productId(10L)
                                .customerId("customer-1")
                                .build();

                Product limitedProduct = Product.builder().id(10L).name("CRM").maxActiveTickets(1).build();

                when(ticketRepository.findById(204L)).thenReturn(Optional.of(existing));
                when(userRepository.findById("agent-1")).thenReturn(Optional.of(agent));
                when(productRepository.findById(10L)).thenReturn(Optional.of(limitedProduct));
                when(agentProductLimitRepository.findByAgentIdAndProductId("agent-1", 10L)).thenReturn(Optional.empty());
                when(ticketClaimRepository.countActiveTicketsByAgentAndProduct("agent-1", 10L)).thenReturn(1L);

                var ex = assertThrows(com.ticketsystem.it_service_backend.exception.TicketLimitExceededException.class,
                                () -> ticketService.claimTicket(204L, "agent-1"));

                assertEquals("error.ticket.limit.exceeded", ex.getMessage());
                verify(ticketRepository, never()).save(any(Ticket.class));
        }

        @Test
        void updateTicketStatus_whenResolved_setsResolvedAtAndPausesSla() {
                Ticket existing = Ticket.builder()
                                .id(304L)
                                .title("Ticket")
                                .description("desc")
                                .priority("HIGH")
                                .status("IN_PROGRESS")
                                .productId(10L)
                                .customerId("customer-1")

                                .build();

                when(ticketRepository.findById(304L)).thenReturn(Optional.of(existing));
                when(ticketClaimRepository.existsByTicketIdAndAgentId(304L, "agent-1")).thenReturn(true);
                when(resolutionNoteRepository.existsByTicketId(304L)).thenReturn(true);
                when(ticketRepository.save(any(Ticket.class))).thenAnswer(invocation -> invocation.getArgument(0));

                Ticket updated = ticketService.updateTicketStatus(304L, "RESOLVED", "agent-1", List.of("AGENT"));

                assertEquals("RESOLVED", updated.getStatus());
                assertNotNull(updated.getResolvedAt());
                verify(workflowService).pauseSla(updated);
                verify(ticketRepository, times(2)).save(any(Ticket.class));
        }

        @Test
        void updateTicketStatus_whenWaitingForCustomer_resumesLaterOnInProgress() {
                Ticket waiting = Ticket.builder()
                                .id(305L)
                                .title("Ticket")
                                .description("desc")
                                .priority("HIGH")
                                .status("WAITING_FOR_CUSTOMER")
                                .productId(10L)
                                .customerId("customer-1")
                                .build();
                Ticket resumed = Ticket.builder()
                                .id(305L)
                                .title("Ticket")
                                .description("desc")
                                .priority("HIGH")
                                .status("WAITING_FOR_CUSTOMER")
                                .productId(10L)
                                .customerId("customer-1")
                                .build();

                when(ticketRepository.findById(305L)).thenReturn(Optional.of(waiting));
                when(ticketRepository.save(any(Ticket.class))).thenAnswer(invocation -> invocation.getArgument(0));

                Ticket updated = ticketService.updateTicketStatus(305L, "IN_PROGRESS", "customer-1", List.of("CUSTOMER"));

                assertEquals("IN_PROGRESS", updated.getStatus());
                verify(workflowService).resumeSla(updated);
                verify(ticketRepository, times(2)).save(any(Ticket.class));
        }

    @Test
    void claimTicket_whenTicketResolved_throwsBadRequest() {
        Ticket existing = Ticket.builder()
                .id(201L)
                .title("Already resolved")
                .description("desc")
                .priority("MEDIUM")
                .status("RESOLVED")
                .productId(10L)
                .customerId("customer-1")
                .build();

        when(ticketRepository.findById(201L)).thenReturn(Optional.of(existing));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> ticketService.claimTicket(201L, "agent-1"));

        assertEquals(400, ex.getStatusCode().value());
        assertEquals("error.ticket.claim.invalid.status", ex.getReason());
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
        when(workflowService.getSlaTimerInfo(existing)).thenReturn(Map.<String, Object>of("deadlineTs", 12345L));

        Map<String, Object> result = ticketService.getSlaTimerInfo(existing);

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
        when(ticketClaimRepository.existsByTicketIdAndAgentId(601L, "agent-1")).thenReturn(true);
        when(ticketRepository.save(any(Ticket.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Ticket updated = ticketService.updateTicketStatus(601L, "IN_PROGRESS", "agent-1", List.of("AGENT"));

        assertEquals("IN_PROGRESS", updated.getStatus());
        assertNull(updated.getResolvedAt());
        verify(workflowService).syncTicketStatus(updated);
    }

    @Test
    void updateTicketStatus_whenResolvedWithoutResolutionNote_throwsBadRequest() {
        Ticket existing = Ticket.builder()
                .id(602L)
                .title("Ticket")
                .description("desc")
                .priority("HIGH")
                .status("IN_PROGRESS")
                .productId(10L)
                .customerId("customer-1")
                .build();

        when(ticketRepository.findById(602L)).thenReturn(Optional.of(existing));
        when(ticketClaimRepository.existsByTicketIdAndAgentId(602L, "agent-1")).thenReturn(true);
        when(resolutionNoteRepository.existsByTicketId(602L)).thenReturn(false);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> ticketService.updateTicketStatus(602L, "RESOLVED", "agent-1", List.of("AGENT")));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        verify(ticketRepository, never()).save(any(Ticket.class));
    }

    @Test
    void updateTicketStatus_whenCurrentStatusUnknown_throwsBadRequest() {
        Ticket existing = Ticket.builder()
                .id(603L)
                .title("Ticket")
                .description("desc")
                .priority("HIGH")
                .status("UNKNOWN_STATE")
                .productId(10L)
                .customerId("customer-1")
                .build();

        when(ticketRepository.findById(603L)).thenReturn(Optional.of(existing));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> ticketService.updateTicketStatus(603L, "NEW", "agent-1", List.of("AGENT")));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        verify(ticketRepository, never()).save(any(Ticket.class));
    }

    @Test
    void updateTicketStatus_whenCustomerNotOwner_throwsForbidden() {
        Ticket existing = Ticket.builder()
                .id(604L)
                .title("Ticket")
                .description("desc")
                .priority("HIGH")
                .status("RESOLVED")
                .productId(10L)
                .customerId("customer-2")
                .build();

        when(ticketRepository.findById(604L)).thenReturn(Optional.of(existing));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> ticketService.updateTicketStatus(604L, "CLOSED", "customer-1", List.of("CUSTOMER")));

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
    }

    @Test
    void updateTicketStatus_whenAgentUnauthorizedForProduct_throwsForbidden() {
        Ticket existing = Ticket.builder()
                .id(605L)
                .title("Ticket")
                .description("desc")
                .priority("HIGH")
                .status("IN_PROGRESS")
                .productId(99L)
                .customerId("customer-1")
                .build();

        when(ticketRepository.findById(605L)).thenReturn(Optional.of(existing));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> ticketService.updateTicketStatus(605L, "NEW", "agent-1", List.of("AGENT")));

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
    }

    @Test
    void updateTicketStatus_whenInProgressToNew_clearsAssigneeAndSyncsStatus() {
        Ticket existing = Ticket.builder()
                .id(606L)
                .title("Ticket")
                .description("desc")
                .priority("HIGH")
                .status("IN_PROGRESS")
                .productId(10L)
                .customerId("customer-1")
                .build();

        when(ticketRepository.findById(606L)).thenReturn(Optional.of(existing));
        when(ticketClaimRepository.existsByTicketIdAndAgentId(606L, "agent-1")).thenReturn(true);
        when(ticketRepository.save(any(Ticket.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Ticket updated = ticketService.updateTicketStatus(606L, "NEW", "agent-1", List.of("AGENT"));

        assertEquals("NEW", updated.getStatus());
        verify(ticketClaimRepository).deleteByTicketId(606L);
        verify(workflowService).syncTicketStatus(updated);
    }

    @Test
    void updateTicketStatus_whenClosed_setsClosedAtAndClosesWorkflow() {
        Ticket existing = Ticket.builder()
                .id(607L)
                .title("Ticket")
                .description("desc")
                .priority("HIGH")
                .status("RESOLVED")
                .productId(10L)
                .customerId("customer-1")
                .build();

        when(ticketRepository.findById(607L)).thenReturn(Optional.of(existing));
        when(ticketRepository.save(any(Ticket.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Ticket updated = ticketService.updateTicketStatus(607L, "CLOSED", "customer-1", List.of("CUSTOMER"));

        assertEquals("CLOSED", updated.getStatus());
        assertNotNull(updated.getClosedAt());
        verify(workflowService).closeTicketWorkflow(updated);
    }

    @Test
    void updateTicketStatus_whenWorkflowSyncFails_stillUpdatesTicket() {
        Ticket existing = Ticket.builder()
                .id(608L)
                .title("Ticket")
                .description("desc")
                .priority("HIGH")
                .status("NEW")
                .productId(10L)
                .customerId("customer-1")
                .build();

        when(ticketRepository.findById(608L)).thenReturn(Optional.of(existing));
        when(ticketClaimRepository.existsByTicketIdAndAgentId(608L, "agent-1")).thenReturn(true);
        when(ticketRepository.save(any(Ticket.class))).thenAnswer(invocation -> invocation.getArgument(0));
        doThrow(new RuntimeException("workflow unavailable")).when(workflowService).syncTicketStatus(any(Ticket.class));

        Ticket updated = ticketService.updateTicketStatus(608L, "IN_PROGRESS", "agent-1", List.of("AGENT"));

        assertEquals("IN_PROGRESS", updated.getStatus());
        verify(ticketRepository, times(1)).save(any(Ticket.class));
    }

    // =============================================================================
    // assignTicket
    // =============================================================================

    @Test
    void assignTicket_whenTicketClosed_throwsBadRequest() {
        Ticket closed = Ticket.builder()
                .id(700L).status("CLOSED").productId(10L).build();
        when(ticketRepository.findById(700L)).thenReturn(Optional.of(closed));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> ticketService.assignTicket(700L, "agent-1", "admin-1", null));

        assertEquals(org.springframework.http.HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    void assignTicket_whenAdminNotAuthorizedForProduct_throwsForbidden() {
        Ticket ticket = Ticket.builder()
                .id(701L).status("NEW").productId(10L).build();
        User adminUser = User.builder()
                .id("admin-1").authorizedProducts(List.of()).build();

        when(ticketRepository.findById(701L)).thenReturn(Optional.of(ticket));
        when(userRepository.findById("admin-1")).thenReturn(Optional.of(adminUser));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> ticketService.assignTicket(701L, "agent-1", "admin-1", null));

        assertEquals(org.springframework.http.HttpStatus.FORBIDDEN, ex.getStatusCode());
    }

    @Test
    void assignTicket_whenAgentNotAuthorizedForProduct_throwsForbidden() {
        Ticket ticket = Ticket.builder()
                .id(702L).status("NEW").productId(10L).build();
        User adminUser = User.builder()
                .id("admin-1").authorizedProducts(List.of(product)).build();
        User unauthorizedAgent = User.builder()
                .id("agent-noperm").authorizedProducts(List.of()).build();

        when(ticketRepository.findById(702L)).thenReturn(Optional.of(ticket));
        when(userRepository.findById("admin-1")).thenReturn(Optional.of(adminUser));
        when(userRepository.findById("agent-noperm")).thenReturn(Optional.of(unauthorizedAgent));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> ticketService.assignTicket(702L, "agent-noperm", "admin-1", null));

        assertEquals(org.springframework.http.HttpStatus.FORBIDDEN, ex.getStatusCode());
    }

    @Test
    void assignTicket_whenCapacityExceeded_throwsBadRequest() {
        Ticket ticket = Ticket.builder()
                .id(703L).status("NEW").productId(10L).build();
        Product limitedProduct = Product.builder()
                .id(10L).maxActiveTickets(2).build();
        User adminUser = User.builder()
                .id("admin-1").authorizedProducts(List.of(limitedProduct)).build();

        when(ticketRepository.findById(703L)).thenReturn(Optional.of(ticket));
        when(userRepository.findById("admin-1")).thenReturn(Optional.of(adminUser));
        when(userRepository.findById("agent-1")).thenReturn(Optional.of(agent));
        when(productRepository.findById(10L)).thenReturn(Optional.of(limitedProduct));
        when(agentProductLimitRepository.findByAgentIdAndProductId("agent-1", 10L))
                .thenReturn(Optional.empty());
        when(ticketClaimRepository.countActiveTicketsByAgentAndProduct("agent-1", 10L))
                .thenReturn(2L); // at limit

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> ticketService.assignTicket(703L, "agent-1", "admin-1", null));

        assertEquals(org.springframework.http.HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    void assignTicket_whenNewTicket_setsInProgressAndCreatesClaim() {
        Ticket ticket = Ticket.builder()
                .id(704L).status("NEW").productId(10L).build();
        Product unlimited = Product.builder().id(10L).maxActiveTickets(null).build();
        User adminUser = User.builder()
                .id("admin-1").authorizedProducts(List.of(unlimited)).build();

        when(ticketRepository.findById(704L)).thenReturn(Optional.of(ticket));
        when(userRepository.findById("admin-1")).thenReturn(Optional.of(adminUser));
        when(userRepository.findById("agent-1")).thenReturn(Optional.of(agent));
        when(productRepository.findById(10L)).thenReturn(Optional.of(unlimited));
        when(agentProductLimitRepository.findByAgentIdAndProductId("agent-1", 10L))
                .thenReturn(Optional.empty());
        when(ticketClaimRepository.existsByTicketIdAndAgentId(704L, "agent-1")).thenReturn(false);
        when(ticketRepository.save(any(Ticket.class))).thenAnswer(inv -> inv.getArgument(0));
        when(ticketClaimRepository.save(any(TicketClaim.class))).thenAnswer(inv -> inv.getArgument(0));

        Ticket result = ticketService.assignTicket(704L, "agent-1", "admin-1", "Test note");

        assertEquals("IN_PROGRESS", result.getStatus());
        verify(ticketClaimRepository).save(any(TicketClaim.class));
        verify(notificationService).notifyTicketAssigned(ticket, "agent-1", "admin-1");
    }

    @Test
    void assignTicket_whenInProgressTicket_statusUnchanged() {
        Ticket ticket = Ticket.builder()
                .id(705L).status("IN_PROGRESS").productId(10L).build();
        Product unlimited = Product.builder().id(10L).maxActiveTickets(null).build();
        User adminUser = User.builder()
                .id("admin-1").authorizedProducts(List.of(unlimited)).build();

        when(ticketRepository.findById(705L)).thenReturn(Optional.of(ticket));
        when(userRepository.findById("admin-1")).thenReturn(Optional.of(adminUser));
        when(userRepository.findById("agent-1")).thenReturn(Optional.of(agent));
        when(productRepository.findById(10L)).thenReturn(Optional.of(unlimited));
        when(agentProductLimitRepository.findByAgentIdAndProductId("agent-1", 10L))
                .thenReturn(Optional.empty());
        when(ticketClaimRepository.existsByTicketIdAndAgentId(705L, "agent-1")).thenReturn(false);
        when(ticketClaimRepository.save(any(TicketClaim.class))).thenAnswer(inv -> inv.getArgument(0));

        Ticket result = ticketService.assignTicket(705L, "agent-1", "admin-1", null);

        assertEquals("IN_PROGRESS", result.getStatus());
        verify(ticketRepository, never()).save(any(Ticket.class));
    }

    @Test
    void assignTicket_whenAlreadyClaimed_returnsEarlyWithoutDuplicate() {
        Ticket ticket = Ticket.builder()
                .id(706L).status("IN_PROGRESS").productId(10L).build();
        Product unlimited = Product.builder().id(10L).maxActiveTickets(null).build();
        User adminUser = User.builder()
                .id("admin-1").authorizedProducts(List.of(unlimited)).build();

        when(ticketRepository.findById(706L)).thenReturn(Optional.of(ticket));
        when(userRepository.findById("admin-1")).thenReturn(Optional.of(adminUser));
        when(userRepository.findById("agent-1")).thenReturn(Optional.of(agent));
        when(productRepository.findById(10L)).thenReturn(Optional.of(unlimited));
        when(agentProductLimitRepository.findByAgentIdAndProductId("agent-1", 10L))
                .thenReturn(Optional.empty());
        when(ticketClaimRepository.existsByTicketIdAndAgentId(706L, "agent-1")).thenReturn(true);

        Ticket result = ticketService.assignTicket(706L, "agent-1", "admin-1", null);

        assertNotNull(result);
        verify(ticketClaimRepository, never()).save(any(TicketClaim.class));
    }

    // =========================================================================
    // createTicket — inactive product branch
    // =========================================================================

    @Test
    @DisplayName("createTicket → ürün inaktifse 422 fırlatır")
    void createTicket_whenProductInactive_throwsUnprocessableEntity() {
        Product inactive = Product.builder().id(10L).name("CRM").isActive(false).build();
        User cust = User.builder().id("c1").authorizedProducts(List.of(inactive)).build();
        Ticket input = Ticket.builder().title("T").description("D").priority("LOW").productId(10L).build();

        when(userRepository.findById("c1")).thenReturn(Optional.of(cust));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> ticketService.createTicket(input, "c1"));

        assertEquals(422, ex.getStatusCode().value());
    }

    // =========================================================================
    // unclaimTicket
    // =========================================================================

    @Test
    @DisplayName("unclaimTicket → claim yoksa BAD_REQUEST")
    void unclaimTicket_whenNoClaim_throwsBadRequest() {
        Ticket t = Ticket.builder().id(800L).status("IN_PROGRESS").build();
        when(ticketRepository.findById(800L)).thenReturn(Optional.of(t));
        when(ticketClaimRepository.existsByTicketIdAndAgentId(800L, "agent-1")).thenReturn(false);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> ticketService.unclaimTicket(800L, "agent-1", null));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    @DisplayName("unclaimTicket → son claim bırakılırsa bilet NEW'e döner")
    void unclaimTicket_whenLastClaim_revertsTicketToNew() {
        Ticket t = Ticket.builder().id(801L).status("IN_PROGRESS").build();
        when(ticketRepository.findById(801L)).thenReturn(Optional.of(t));
        when(ticketClaimRepository.existsByTicketIdAndAgentId(801L, "agent-1")).thenReturn(true);
        when(ticketClaimRepository.countByTicketId(801L)).thenReturn(0L);
        when(ticketRepository.save(any(Ticket.class))).thenAnswer(inv -> inv.getArgument(0));

        Ticket result = ticketService.unclaimTicket(801L, "agent-1", "giving up");

        assertEquals("NEW", result.getStatus());
        verify(ticketClaimRepository).deleteByTicketIdAndAgentId(801L, "agent-1");
        verify(ticketAuditLogRepository).save(any());
    }

    @Test
    @DisplayName("unclaimTicket → başka claimerlar varsa statü değişmez")
    void unclaimTicket_whenOtherClaimersExist_statusUnchanged() {
        Ticket t = Ticket.builder().id(802L).status("IN_PROGRESS").build();
        when(ticketRepository.findById(802L)).thenReturn(Optional.of(t));
        when(ticketClaimRepository.existsByTicketIdAndAgentId(802L, "agent-1")).thenReturn(true);
        when(ticketClaimRepository.countByTicketId(802L)).thenReturn(1L);

        Ticket result = ticketService.unclaimTicket(802L, "agent-1", null);

        assertEquals("IN_PROGRESS", result.getStatus());
        verify(ticketRepository, never()).save(any(Ticket.class));
    }

    // =========================================================================
    // Non-paged list methods
    // =========================================================================

    @Test
    @DisplayName("getTeamTickets → AGENT_ADMIN tüm ürünlere ait aktif biletleri döner")
    void getTeamTickets_agentAdmin_returnsActiveTickets() {
        Ticket t = Ticket.builder().id(900L).productId(10L).build();
        when(ticketRepository.findAll()).thenReturn(List.of(t));
        when(ticketRepository.findActiveByProductIdIn(any())).thenReturn(List.of(t));

        List<Ticket> result = ticketService.getTeamTickets("admin-1", List.of("AGENT_ADMIN"));

        assertEquals(1, result.size());
    }

    @Test
    @DisplayName("getTeamTickets → userId null → boş liste")
    void getTeamTickets_nullUserId_returnsEmpty() {
        List<Ticket> result = ticketService.getTeamTickets(null, List.of("AGENT"));
        assertEquals(0, result.size());
    }

    @Test
    @DisplayName("getTeamTickets → agent yetkili ürün yok → boş liste")
    void getTeamTickets_agent_noProducts_returnsEmpty() {
        User agentNoProducts = User.builder().id("agent-2").authorizedProducts(List.of()).build();
        when(userRepository.findById("agent-2")).thenReturn(Optional.of(agentNoProducts));

        List<Ticket> result = ticketService.getTeamTickets("agent-2", List.of("AGENT"));
        assertEquals(0, result.size());
    }

    @Test
    @DisplayName("getTeamTickets → agent yetkili ürünlerdeki aktif biletleri döner")
    void getTeamTickets_agent_withProducts_returnsActive() {
        Ticket t = Ticket.builder().id(901L).productId(10L).status("IN_PROGRESS").build();
        when(userRepository.findById("agent-1")).thenReturn(Optional.of(agent));
        when(ticketRepository.findActiveByProductIdIn(List.of(10L))).thenReturn(List.of(t));

        List<Ticket> result = ticketService.getTeamTickets("agent-1", List.of("AGENT"));
        assertEquals(1, result.size());
    }

    @Test
    @DisplayName("getTicketsByProduct → AGENT rolü tüm biletleri döner")
    void getTicketsByProduct_agentRole_returnsAll() {
        Ticket t = Ticket.builder().id(902L).productId(10L).build();
        when(ticketRepository.findByProductId(10L)).thenReturn(List.of(t));

        List<Ticket> result = ticketService.getTicketsByProduct(10L, "agent-1", List.of("AGENT"));
        assertEquals(1, result.size());
    }

    @Test
    @DisplayName("getTicketsByProduct → CUSTOMER rolü sadece kendi biletlerini döner")
    void getTicketsByProduct_customerRole_returnsOwnTickets() {
        Ticket t = Ticket.builder().id(903L).productId(10L).customerId("customer-1").build();
        when(ticketRepository.findByCustomerIdAndProductId("customer-1", 10L)).thenReturn(List.of(t));

        List<Ticket> result = ticketService.getTicketsByProduct(10L, "customer-1", List.of("CUSTOMER"));
        assertEquals(1, result.size());
    }

    @Test
    @DisplayName("getTicketsByProduct → bilinmeyen rol → boş liste")
    void getTicketsByProduct_unknownRole_returnsEmpty() {
        List<Ticket> result = ticketService.getTicketsByProduct(10L, "user-1", List.of("VIEWER"));
        assertEquals(0, result.size());
    }

    @Test
    @DisplayName("getAgentClaimedTickets → claim yoksa boş liste")
    void getAgentClaimedTickets_noClaims_returnsEmpty() {
        when(ticketClaimRepository.findTicketIdsByAgentId("agent-1")).thenReturn(List.of());

        List<Ticket> result = ticketService.getAgentClaimedTickets("agent-1");
        assertEquals(0, result.size());
    }

    @Test
    @DisplayName("getAgentClaimedTickets → claimler varsa biletleri döner")
    void getAgentClaimedTickets_withClaims_returnsTickets() {
        Ticket t = Ticket.builder().id(904L).build();
        when(ticketClaimRepository.findTicketIdsByAgentId("agent-1")).thenReturn(List.of(904L));
        when(ticketRepository.findAllById(List.of(904L))).thenReturn(List.of(t));

        List<Ticket> result = ticketService.getAgentClaimedTickets("agent-1");
        assertEquals(1, result.size());
    }

    // =========================================================================
    // getCustomerTicketsFiltered — sort/filter branch coverage
    // =========================================================================

    @Test
    @DisplayName("getCustomerTicketsFiltered → priority ASC sort")
    void getCustomerTicketsFiltered_sortByPriorityAsc() {
        Pageable p = PageRequest.of(0, 10, Sort.by(Sort.Direction.ASC, "priority"));
        when(ticketRepository.findByCustomerIdFilteredOrderByPriorityAsc(any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));

        Page<Ticket> result = ticketService.getCustomerTicketsFiltered("customer-1", TicketFilterDTO.builder().build(), p);
        assertNotNull(result);
    }

    @Test
    @DisplayName("getCustomerTicketsFiltered → priority DESC sort")
    void getCustomerTicketsFiltered_sortByPriorityDesc() {
        Pageable p = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "priority"));
        when(ticketRepository.findByCustomerIdFilteredOrderByPriorityDesc(any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));

        Page<Ticket> result = ticketService.getCustomerTicketsFiltered("customer-1", TicketFilterDTO.builder().build(), p);
        assertNotNull(result);
    }

    @Test
    @DisplayName("getCustomerTicketsFiltered → slaDeadline ASC sort")
    void getCustomerTicketsFiltered_sortBySlaAsc() {
        Pageable p = PageRequest.of(0, 10, Sort.by(Sort.Direction.ASC, "slaDeadline"));
        when(ticketRepository.findByCustomerIdFilteredOrderBySlaUrgencyAsc(any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));

        Page<Ticket> result = ticketService.getCustomerTicketsFiltered("customer-1", TicketFilterDTO.builder().build(), p);
        assertNotNull(result);
    }

    @Test
    @DisplayName("getCustomerTicketsFiltered → slaDeadline DESC sort")
    void getCustomerTicketsFiltered_sortBySlaDesc() {
        Pageable p = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "slaDeadline"));
        when(ticketRepository.findByCustomerIdFilteredOrderBySlaUrgencyDesc(any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));

        Page<Ticket> result = ticketService.getCustomerTicketsFiltered("customer-1", TicketFilterDTO.builder().build(), p);
        assertNotNull(result);
    }

    @Test
    @DisplayName("getCustomerTicketsFiltered → search alanı dolu → full filtered")
    void getCustomerTicketsFiltered_withSearch_callsFullFiltered() {
        Pageable p = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "createdAt"));
        TicketFilterDTO f = TicketFilterDTO.builder().search("keyword").build();
        when(productRepository.findAll()).thenReturn(List.of());
        when(ticketRepository.findByCustomerIdFullFiltered(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));

        Page<Ticket> result = ticketService.getCustomerTicketsFiltered("customer-1", f, p);
        assertNotNull(result);
    }

    @Test
    @DisplayName("getCustomerTicketsFiltered → varsayılan path")
    void getCustomerTicketsFiltered_default() {
        Pageable p = PageRequest.of(0, 10);
        when(ticketRepository.findByCustomerIdFiltered(any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));

        Page<Ticket> result = ticketService.getCustomerTicketsFiltered("customer-1", TicketFilterDTO.builder().build(), p);
        assertNotNull(result);
    }

    // =========================================================================
    // getPoolTicketsFiltered — AGENT_ADMIN & AGENT branch coverage
    // =========================================================================

    @Test
    @DisplayName("getPoolTicketsFiltered → AGENT_ADMIN priority ASC")
    void getPoolTicketsFiltered_agentAdmin_sortPriorityAsc() {
        Pageable p = PageRequest.of(0, 10, Sort.by(Sort.Direction.ASC, "priority"));
        when(userRepository.findById("admin-1")).thenReturn(Optional.of(agentAdmin));
        when(ticketRepository.findPoolTicketsFilteredOrderByPriorityAsc(any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));

        Page<Ticket> result = ticketService.getPoolTicketsFiltered("admin-1", List.of("AGENT_ADMIN"), TicketFilterDTO.builder().build(), p);
        assertNotNull(result);
    }

    @Test
    @DisplayName("getPoolTicketsFiltered → AGENT_ADMIN priority DESC")
    void getPoolTicketsFiltered_agentAdmin_sortPriorityDesc() {
        Pageable p = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "priority"));
        when(userRepository.findById("admin-1")).thenReturn(Optional.of(agentAdmin));
        when(ticketRepository.findPoolTicketsFilteredOrderByPriorityDesc(any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));

        Page<Ticket> result = ticketService.getPoolTicketsFiltered("admin-1", List.of("AGENT_ADMIN"), TicketFilterDTO.builder().build(), p);
        assertNotNull(result);
    }

    @Test
    @DisplayName("getPoolTicketsFiltered → AGENT_ADMIN SLA ASC")
    void getPoolTicketsFiltered_agentAdmin_sortSlaAsc() {
        Pageable p = PageRequest.of(0, 10, Sort.by(Sort.Direction.ASC, "slaDeadline"));
        when(userRepository.findById("admin-1")).thenReturn(Optional.of(agentAdmin));
        when(ticketRepository.findPoolTicketsFilteredOrderBySlaUrgencyAsc(any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));

        Page<Ticket> result = ticketService.getPoolTicketsFiltered("admin-1", List.of("AGENT_ADMIN"), TicketFilterDTO.builder().build(), p);
        assertNotNull(result);
    }

    @Test
    @DisplayName("getPoolTicketsFiltered → AGENT_ADMIN SLA DESC")
    void getPoolTicketsFiltered_agentAdmin_sortSlaDesc() {
        Pageable p = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "slaDeadline"));
        when(userRepository.findById("admin-1")).thenReturn(Optional.of(agentAdmin));
        when(ticketRepository.findPoolTicketsFilteredOrderBySlaUrgencyDesc(any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));

        Page<Ticket> result = ticketService.getPoolTicketsFiltered("admin-1", List.of("AGENT_ADMIN"), TicketFilterDTO.builder().build(), p);
        assertNotNull(result);
    }

    @Test
    @DisplayName("getPoolTicketsFiltered → AGENT_ADMIN full filtered")
    void getPoolTicketsFiltered_agentAdmin_withSearch() {
        Pageable p = PageRequest.of(0, 10);
        TicketFilterDTO f = TicketFilterDTO.builder().search("test").build();
        when(userRepository.findById("admin-1")).thenReturn(Optional.of(agentAdmin));
        when(productRepository.findAll()).thenReturn(List.of());
        when(ticketRepository.findPoolTicketsFullFiltered(any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));

        Page<Ticket> result = ticketService.getPoolTicketsFiltered("admin-1", List.of("AGENT_ADMIN"), f, p);
        assertNotNull(result);
    }

    @Test
    @DisplayName("getPoolTicketsFiltered → AGENT_ADMIN default")
    void getPoolTicketsFiltered_agentAdmin_default() {
        Pageable p = PageRequest.of(0, 10);
        when(userRepository.findById("admin-1")).thenReturn(Optional.of(agentAdmin));
        when(ticketRepository.findPoolTicketsFiltered(any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));

        Page<Ticket> result = ticketService.getPoolTicketsFiltered("admin-1", List.of("AGENT_ADMIN"), TicketFilterDTO.builder().build(), p);
        assertNotNull(result);
    }

    @Test
    @DisplayName("getPoolTicketsFiltered → AGENT userId null → boş sayfa")
    void getPoolTicketsFiltered_agent_nullUserId_returnsEmpty() {
        Page<Ticket> result = ticketService.getPoolTicketsFiltered(null, List.of("AGENT"), TicketFilterDTO.builder().build(), PageRequest.of(0, 10));
        assertNotNull(result);
        assertEquals(0, result.getTotalElements());
    }

    @Test
    @DisplayName("getPoolTicketsFiltered → AGENT ürün yok → boş sayfa")
    void getPoolTicketsFiltered_agent_noProducts_returnsEmpty() {
        User agentNoProducts = User.builder().id("agent-2").authorizedProducts(List.of()).build();
        when(userRepository.findById("agent-2")).thenReturn(Optional.of(agentNoProducts));

        Page<Ticket> result = ticketService.getPoolTicketsFiltered("agent-2", List.of("AGENT"), TicketFilterDTO.builder().build(), PageRequest.of(0, 10));
        assertNotNull(result);
        assertEquals(0, result.getTotalElements());
    }

    @Test
    @DisplayName("getPoolTicketsFiltered → AGENT priority ASC")
    void getPoolTicketsFiltered_agent_sortPriorityAsc() {
        Pageable p = PageRequest.of(0, 10, Sort.by(Sort.Direction.ASC, "priority"));
        when(userRepository.findById("agent-1")).thenReturn(Optional.of(agent));
        when(ticketRepository.findPoolTicketsFilteredOrderByPriorityAsc(any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));

        Page<Ticket> result = ticketService.getPoolTicketsFiltered("agent-1", List.of("AGENT"), TicketFilterDTO.builder().build(), p);
        assertNotNull(result);
    }

    @Test
    @DisplayName("getPoolTicketsFiltered → AGENT priority DESC")
    void getPoolTicketsFiltered_agent_sortPriorityDesc() {
        Pageable p = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "priority"));
        when(userRepository.findById("agent-1")).thenReturn(Optional.of(agent));
        when(ticketRepository.findPoolTicketsFilteredOrderByPriorityDesc(any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));

        Page<Ticket> result = ticketService.getPoolTicketsFiltered("agent-1", List.of("AGENT"), TicketFilterDTO.builder().build(), p);
        assertNotNull(result);
    }

    @Test
    @DisplayName("getPoolTicketsFiltered → AGENT SLA ASC")
    void getPoolTicketsFiltered_agent_sortSlaAsc() {
        Pageable p = PageRequest.of(0, 10, Sort.by(Sort.Direction.ASC, "slaDeadline"));
        when(userRepository.findById("agent-1")).thenReturn(Optional.of(agent));
        when(ticketRepository.findPoolTicketsFilteredOrderBySlaUrgencyAsc(any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));

        Page<Ticket> result = ticketService.getPoolTicketsFiltered("agent-1", List.of("AGENT"), TicketFilterDTO.builder().build(), p);
        assertNotNull(result);
    }

    @Test
    @DisplayName("getPoolTicketsFiltered → AGENT SLA DESC")
    void getPoolTicketsFiltered_agent_sortSlaDesc() {
        Pageable p = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "slaDeadline"));
        when(userRepository.findById("agent-1")).thenReturn(Optional.of(agent));
        when(ticketRepository.findPoolTicketsFilteredOrderBySlaUrgencyDesc(any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));

        Page<Ticket> result = ticketService.getPoolTicketsFiltered("agent-1", List.of("AGENT"), TicketFilterDTO.builder().build(), p);
        assertNotNull(result);
    }

    @Test
    @DisplayName("getPoolTicketsFiltered → AGENT full filtered")
    void getPoolTicketsFiltered_agent_withSearch() {
        Pageable p = PageRequest.of(0, 10);
        TicketFilterDTO f = TicketFilterDTO.builder().search("issue").build();
        when(userRepository.findById("agent-1")).thenReturn(Optional.of(agent));
        when(productRepository.findAll()).thenReturn(List.of());
        when(ticketRepository.findPoolTicketsFullFiltered(any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));

        Page<Ticket> result = ticketService.getPoolTicketsFiltered("agent-1", List.of("AGENT"), f, p);
        assertNotNull(result);
    }

    @Test
    @DisplayName("getPoolTicketsFiltered → AGENT default")
    void getPoolTicketsFiltered_agent_default() {
        Pageable p = PageRequest.of(0, 10);
        when(userRepository.findById("agent-1")).thenReturn(Optional.of(agent));
        when(ticketRepository.findPoolTicketsFiltered(any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));

        Page<Ticket> result = ticketService.getPoolTicketsFiltered("agent-1", List.of("AGENT"), TicketFilterDTO.builder().build(), p);
        assertNotNull(result);
    }

    // =========================================================================
    // getTeamTicketsFiltered — branch coverage
    // =========================================================================

    @Test
    @DisplayName("getTeamTicketsFiltered → AGENT_ADMIN priority ASC")
    void getTeamTicketsFiltered_agentAdmin_sortPriorityAsc() {
        Pageable p = PageRequest.of(0, 10, Sort.by(Sort.Direction.ASC, "priority"));
        when(userRepository.findById("admin-1")).thenReturn(Optional.of(agentAdmin));
        when(ticketRepository.findTeamTicketsFilteredOrderByPriorityAsc(any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));

        Page<Ticket> result = ticketService.getTeamTicketsFiltered("admin-1", List.of("AGENT_ADMIN"), TicketFilterDTO.builder().build(), p);
        assertNotNull(result);
    }

    @Test
    @DisplayName("getTeamTicketsFiltered → AGENT_ADMIN priority DESC")
    void getTeamTicketsFiltered_agentAdmin_sortPriorityDesc() {
        Pageable p = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "priority"));
        when(userRepository.findById("admin-1")).thenReturn(Optional.of(agentAdmin));
        when(ticketRepository.findTeamTicketsFilteredOrderByPriorityDesc(any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));

        Page<Ticket> result = ticketService.getTeamTicketsFiltered("admin-1", List.of("AGENT_ADMIN"), TicketFilterDTO.builder().build(), p);
        assertNotNull(result);
    }

    @Test
    @DisplayName("getTeamTicketsFiltered → AGENT_ADMIN SLA ASC")
    void getTeamTicketsFiltered_agentAdmin_sortSlaAsc() {
        Pageable p = PageRequest.of(0, 10, Sort.by(Sort.Direction.ASC, "slaDeadline"));
        when(userRepository.findById("admin-1")).thenReturn(Optional.of(agentAdmin));
        when(ticketRepository.findTeamTicketsFilteredOrderBySlaUrgencyAsc(any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));

        Page<Ticket> result = ticketService.getTeamTicketsFiltered("admin-1", List.of("AGENT_ADMIN"), TicketFilterDTO.builder().build(), p);
        assertNotNull(result);
    }

    @Test
    @DisplayName("getTeamTicketsFiltered → AGENT_ADMIN SLA DESC")
    void getTeamTicketsFiltered_agentAdmin_sortSlaDesc() {
        Pageable p = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "slaDeadline"));
        when(userRepository.findById("admin-1")).thenReturn(Optional.of(agentAdmin));
        when(ticketRepository.findTeamTicketsFilteredOrderBySlaUrgencyDesc(any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));

        Page<Ticket> result = ticketService.getTeamTicketsFiltered("admin-1", List.of("AGENT_ADMIN"), TicketFilterDTO.builder().build(), p);
        assertNotNull(result);
    }

    @Test
    @DisplayName("getTeamTicketsFiltered → AGENT_ADMIN full filtered")
    void getTeamTicketsFiltered_agentAdmin_withSearch() {
        Pageable p = PageRequest.of(0, 10);
        TicketFilterDTO f = TicketFilterDTO.builder().search("vpn").build();
        when(userRepository.findById("admin-1")).thenReturn(Optional.of(agentAdmin));
        when(productRepository.findAll()).thenReturn(List.of());
        when(ticketRepository.findTeamTicketsFullFiltered(any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));

        Page<Ticket> result = ticketService.getTeamTicketsFiltered("admin-1", List.of("AGENT_ADMIN"), f, p);
        assertNotNull(result);
    }

    @Test
    @DisplayName("getTeamTicketsFiltered → AGENT_ADMIN default")
    void getTeamTicketsFiltered_agentAdmin_default() {
        Pageable p = PageRequest.of(0, 10);
        when(userRepository.findById("admin-1")).thenReturn(Optional.of(agentAdmin));
        when(ticketRepository.findTeamTicketsFiltered(any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));

        Page<Ticket> result = ticketService.getTeamTicketsFiltered("admin-1", List.of("AGENT_ADMIN"), TicketFilterDTO.builder().build(), p);
        assertNotNull(result);
    }

    @Test
    @DisplayName("getTeamTicketsFiltered → AGENT userId null → boş sayfa")
    void getTeamTicketsFiltered_agent_nullUserId_returnsEmpty() {
        Page<Ticket> result = ticketService.getTeamTicketsFiltered(null, List.of("AGENT"), TicketFilterDTO.builder().build(), PageRequest.of(0, 10));
        assertNotNull(result);
        assertEquals(0, result.getTotalElements());
    }

    @Test
    @DisplayName("getTeamTicketsFiltered → AGENT ürün yok → boş sayfa")
    void getTeamTicketsFiltered_agent_noProducts_returnsEmpty() {
        User agentNoProducts = User.builder().id("agent-2").authorizedProducts(List.of()).build();
        when(userRepository.findById("agent-2")).thenReturn(Optional.of(agentNoProducts));

        Page<Ticket> result = ticketService.getTeamTicketsFiltered("agent-2", List.of("AGENT"), TicketFilterDTO.builder().build(), PageRequest.of(0, 10));
        assertNotNull(result);
        assertEquals(0, result.getTotalElements());
    }

    @Test
    @DisplayName("getTeamTicketsFiltered → AGENT priority ASC")
    void getTeamTicketsFiltered_agent_sortPriorityAsc() {
        Pageable p = PageRequest.of(0, 10, Sort.by(Sort.Direction.ASC, "priority"));
        when(userRepository.findById("agent-1")).thenReturn(Optional.of(agent));
        when(ticketRepository.findTeamTicketsFilteredOrderByPriorityAsc(any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));

        Page<Ticket> result = ticketService.getTeamTicketsFiltered("agent-1", List.of("AGENT"), TicketFilterDTO.builder().build(), p);
        assertNotNull(result);
    }

    @Test
    @DisplayName("getTeamTicketsFiltered → AGENT priority DESC")
    void getTeamTicketsFiltered_agent_sortPriorityDesc() {
        Pageable p = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "priority"));
        when(userRepository.findById("agent-1")).thenReturn(Optional.of(agent));
        when(ticketRepository.findTeamTicketsFilteredOrderByPriorityDesc(any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));

        Page<Ticket> result = ticketService.getTeamTicketsFiltered("agent-1", List.of("AGENT"), TicketFilterDTO.builder().build(), p);
        assertNotNull(result);
    }

    @Test
    @DisplayName("getTeamTicketsFiltered → AGENT SLA ASC")
    void getTeamTicketsFiltered_agent_sortSlaAsc() {
        Pageable p = PageRequest.of(0, 10, Sort.by(Sort.Direction.ASC, "slaDeadline"));
        when(userRepository.findById("agent-1")).thenReturn(Optional.of(agent));
        when(ticketRepository.findTeamTicketsFilteredOrderBySlaUrgencyAsc(any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));

        Page<Ticket> result = ticketService.getTeamTicketsFiltered("agent-1", List.of("AGENT"), TicketFilterDTO.builder().build(), p);
        assertNotNull(result);
    }

    @Test
    @DisplayName("getTeamTicketsFiltered → AGENT SLA DESC")
    void getTeamTicketsFiltered_agent_sortSlaDesc() {
        Pageable p = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "slaDeadline"));
        when(userRepository.findById("agent-1")).thenReturn(Optional.of(agent));
        when(ticketRepository.findTeamTicketsFilteredOrderBySlaUrgencyDesc(any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));

        Page<Ticket> result = ticketService.getTeamTicketsFiltered("agent-1", List.of("AGENT"), TicketFilterDTO.builder().build(), p);
        assertNotNull(result);
    }

    @Test
    @DisplayName("getTeamTicketsFiltered → AGENT full filtered")
    void getTeamTicketsFiltered_agent_withSearch() {
        Pageable p = PageRequest.of(0, 10);
        TicketFilterDTO f = TicketFilterDTO.builder().search("error").build();
        when(userRepository.findById("agent-1")).thenReturn(Optional.of(agent));
        when(productRepository.findAll()).thenReturn(List.of());
        when(ticketRepository.findTeamTicketsFullFiltered(any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));

        Page<Ticket> result = ticketService.getTeamTicketsFiltered("agent-1", List.of("AGENT"), f, p);
        assertNotNull(result);
    }

    @Test
    @DisplayName("getTeamTicketsFiltered → AGENT default")
    void getTeamTicketsFiltered_agent_default() {
        Pageable p = PageRequest.of(0, 10);
        when(userRepository.findById("agent-1")).thenReturn(Optional.of(agent));
        when(ticketRepository.findTeamTicketsFiltered(any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));

        Page<Ticket> result = ticketService.getTeamTicketsFiltered("agent-1", List.of("AGENT"), TicketFilterDTO.builder().build(), p);
        assertNotNull(result);
    }

    // =========================================================================
    // getTicketsByProductFiltered — branch coverage
    // =========================================================================

    @Test
    @DisplayName("getTicketsByProductFiltered → AGENT priority ASC")
    void getTicketsByProductFiltered_agent_sortPriorityAsc() {
        Pageable p = PageRequest.of(0, 10, Sort.by(Sort.Direction.ASC, "priority"));
        when(userRepository.findById("agent-1")).thenReturn(Optional.of(agent));
        when(ticketRepository.findByProductIdFilteredOrderByPriorityAsc(any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));

        Page<Ticket> result = ticketService.getTicketsByProductFiltered(10L, "agent-1", List.of("AGENT"), TicketFilterDTO.builder().build(), p);
        assertNotNull(result);
    }

    @Test
    @DisplayName("getTicketsByProductFiltered → AGENT priority DESC")
    void getTicketsByProductFiltered_agent_sortPriorityDesc() {
        Pageable p = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "priority"));
        when(userRepository.findById("agent-1")).thenReturn(Optional.of(agent));
        when(ticketRepository.findByProductIdFilteredOrderByPriorityDesc(any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));

        Page<Ticket> result = ticketService.getTicketsByProductFiltered(10L, "agent-1", List.of("AGENT"), TicketFilterDTO.builder().build(), p);
        assertNotNull(result);
    }

    @Test
    @DisplayName("getTicketsByProductFiltered → AGENT SLA ASC")
    void getTicketsByProductFiltered_agent_sortSlaAsc() {
        Pageable p = PageRequest.of(0, 10, Sort.by(Sort.Direction.ASC, "slaDeadline"));
        when(userRepository.findById("agent-1")).thenReturn(Optional.of(agent));
        when(ticketRepository.findByProductIdFilteredOrderBySlaUrgencyAsc(any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));

        Page<Ticket> result = ticketService.getTicketsByProductFiltered(10L, "agent-1", List.of("AGENT"), TicketFilterDTO.builder().build(), p);
        assertNotNull(result);
    }

    @Test
    @DisplayName("getTicketsByProductFiltered → AGENT SLA DESC")
    void getTicketsByProductFiltered_agent_sortSlaDesc() {
        Pageable p = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "slaDeadline"));
        when(userRepository.findById("agent-1")).thenReturn(Optional.of(agent));
        when(ticketRepository.findByProductIdFilteredOrderBySlaUrgencyDesc(any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));

        Page<Ticket> result = ticketService.getTicketsByProductFiltered(10L, "agent-1", List.of("AGENT"), TicketFilterDTO.builder().build(), p);
        assertNotNull(result);
    }

    @Test
    @DisplayName("getTicketsByProductFiltered → AGENT full filtered")
    void getTicketsByProductFiltered_agent_withSearch() {
        Pageable p = PageRequest.of(0, 10);
        TicketFilterDTO f = TicketFilterDTO.builder().search("printer").build();
        when(userRepository.findById("agent-1")).thenReturn(Optional.of(agent));
        when(ticketRepository.findByProductIdFullFiltered(any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));

        Page<Ticket> result = ticketService.getTicketsByProductFiltered(10L, "agent-1", List.of("AGENT"), f, p);
        assertNotNull(result);
    }

    @Test
    @DisplayName("getTicketsByProductFiltered → AGENT default")
    void getTicketsByProductFiltered_agent_default() {
        Pageable p = PageRequest.of(0, 10);
        when(userRepository.findById("agent-1")).thenReturn(Optional.of(agent));
        when(ticketRepository.findByProductIdFiltered(any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));

        Page<Ticket> result = ticketService.getTicketsByProductFiltered(10L, "agent-1", List.of("AGENT"), TicketFilterDTO.builder().build(), p);
        assertNotNull(result);
    }

    @Test
    @DisplayName("getTicketsByProductFiltered → CUSTOMER priority ASC")
    void getTicketsByProductFiltered_customer_sortPriorityAsc() {
        Pageable p = PageRequest.of(0, 10, Sort.by(Sort.Direction.ASC, "priority"));
        when(ticketRepository.findByProductIdAndCustomerIdFilteredOrderByPriorityAsc(any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));

        Page<Ticket> result = ticketService.getTicketsByProductFiltered(10L, "customer-1", List.of("CUSTOMER"), TicketFilterDTO.builder().build(), p);
        assertNotNull(result);
    }

    @Test
    @DisplayName("getTicketsByProductFiltered → CUSTOMER priority DESC")
    void getTicketsByProductFiltered_customer_sortPriorityDesc() {
        Pageable p = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "priority"));
        when(ticketRepository.findByProductIdAndCustomerIdFilteredOrderByPriorityDesc(any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));

        Page<Ticket> result = ticketService.getTicketsByProductFiltered(10L, "customer-1", List.of("CUSTOMER"), TicketFilterDTO.builder().build(), p);
        assertNotNull(result);
    }

    @Test
    @DisplayName("getTicketsByProductFiltered → CUSTOMER SLA ASC")
    void getTicketsByProductFiltered_customer_sortSlaAsc() {
        Pageable p = PageRequest.of(0, 10, Sort.by(Sort.Direction.ASC, "slaDeadline"));
        when(ticketRepository.findByProductIdAndCustomerIdFilteredOrderBySlaUrgencyAsc(any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));

        Page<Ticket> result = ticketService.getTicketsByProductFiltered(10L, "customer-1", List.of("CUSTOMER"), TicketFilterDTO.builder().build(), p);
        assertNotNull(result);
    }

    @Test
    @DisplayName("getTicketsByProductFiltered → CUSTOMER SLA DESC")
    void getTicketsByProductFiltered_customer_sortSlaDesc() {
        Pageable p = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "slaDeadline"));
        when(ticketRepository.findByProductIdAndCustomerIdFilteredOrderBySlaUrgencyDesc(any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));

        Page<Ticket> result = ticketService.getTicketsByProductFiltered(10L, "customer-1", List.of("CUSTOMER"), TicketFilterDTO.builder().build(), p);
        assertNotNull(result);
    }

    @Test
    @DisplayName("getTicketsByProductFiltered → CUSTOMER full filtered")
    void getTicketsByProductFiltered_customer_withSearch() {
        Pageable p = PageRequest.of(0, 10);
        TicketFilterDTO f = TicketFilterDTO.builder().search("login").build();
        when(ticketRepository.findByProductIdAndCustomerIdFullFiltered(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));

        Page<Ticket> result = ticketService.getTicketsByProductFiltered(10L, "customer-1", List.of("CUSTOMER"), f, p);
        assertNotNull(result);
    }

    @Test
    @DisplayName("getTicketsByProductFiltered → CUSTOMER default")
    void getTicketsByProductFiltered_customer_default() {
        Pageable p = PageRequest.of(0, 10);
        when(ticketRepository.findByProductIdAndCustomerIdFiltered(any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));

        Page<Ticket> result = ticketService.getTicketsByProductFiltered(10L, "customer-1", List.of("CUSTOMER"), TicketFilterDTO.builder().build(), p);
        assertNotNull(result);
    }

    @Test
    @DisplayName("getTicketsByProductFiltered → bilinmeyen rol → boş sayfa")
    void getTicketsByProductFiltered_unknownRole_returnsEmpty() {
        Page<Ticket> result = ticketService.getTicketsByProductFiltered(10L, "user-1", List.of("VIEWER"), TicketFilterDTO.builder().build(), PageRequest.of(0, 10));
        assertNotNull(result);
        assertEquals(0, result.getTotalElements());
    }

    // =========================================================================
    // getAgentClaimedTicketsFiltered — branch coverage
    // =========================================================================

    @Test
    @DisplayName("getAgentClaimedTicketsFiltered → claim yok → boş sayfa")
    void getAgentClaimedTicketsFiltered_emptyClaims_returnsEmpty() {
        when(ticketClaimRepository.findTicketIdsByAgentId("agent-1")).thenReturn(List.of());

        Page<Ticket> result = ticketService.getAgentClaimedTicketsFiltered("agent-1", TicketFilterDTO.builder().build(), PageRequest.of(0, 10));
        assertNotNull(result);
        assertEquals(0, result.getTotalElements());
    }

    @Test
    @DisplayName("getAgentClaimedTicketsFiltered → priority ASC")
    void getAgentClaimedTicketsFiltered_sortPriorityAsc() {
        Pageable p = PageRequest.of(0, 10, Sort.by(Sort.Direction.ASC, "priority"));
        when(ticketClaimRepository.findTicketIdsByAgentId("agent-1")).thenReturn(List.of(100L));
        when(ticketRepository.findClaimedTicketsFilteredOrderByPriorityAsc(any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));

        Page<Ticket> result = ticketService.getAgentClaimedTicketsFiltered("agent-1", TicketFilterDTO.builder().build(), p);
        assertNotNull(result);
    }

    @Test
    @DisplayName("getAgentClaimedTicketsFiltered → priority DESC")
    void getAgentClaimedTicketsFiltered_sortPriorityDesc() {
        Pageable p = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "priority"));
        when(ticketClaimRepository.findTicketIdsByAgentId("agent-1")).thenReturn(List.of(100L));
        when(ticketRepository.findClaimedTicketsFilteredOrderByPriorityDesc(any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));

        Page<Ticket> result = ticketService.getAgentClaimedTicketsFiltered("agent-1", TicketFilterDTO.builder().build(), p);
        assertNotNull(result);
    }

    @Test
    @DisplayName("getAgentClaimedTicketsFiltered → SLA ASC")
    void getAgentClaimedTicketsFiltered_sortSlaAsc() {
        Pageable p = PageRequest.of(0, 10, Sort.by(Sort.Direction.ASC, "slaDeadline"));
        when(ticketClaimRepository.findTicketIdsByAgentId("agent-1")).thenReturn(List.of(100L));
        when(ticketRepository.findClaimedTicketsFilteredOrderBySlaUrgencyAsc(any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));

        Page<Ticket> result = ticketService.getAgentClaimedTicketsFiltered("agent-1", TicketFilterDTO.builder().build(), p);
        assertNotNull(result);
    }

    @Test
    @DisplayName("getAgentClaimedTicketsFiltered → SLA DESC")
    void getAgentClaimedTicketsFiltered_sortSlaDesc() {
        Pageable p = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "slaDeadline"));
        when(ticketClaimRepository.findTicketIdsByAgentId("agent-1")).thenReturn(List.of(100L));
        when(ticketRepository.findClaimedTicketsFilteredOrderBySlaUrgencyDesc(any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));

        Page<Ticket> result = ticketService.getAgentClaimedTicketsFiltered("agent-1", TicketFilterDTO.builder().build(), p);
        assertNotNull(result);
    }

    @Test
    @DisplayName("getAgentClaimedTicketsFiltered → full filtered")
    void getAgentClaimedTicketsFiltered_withSearch() {
        Pageable p = PageRequest.of(0, 10);
        TicketFilterDTO f = TicketFilterDTO.builder().search("slow").build();
        when(ticketClaimRepository.findTicketIdsByAgentId("agent-1")).thenReturn(List.of(100L));
        when(productRepository.findAll()).thenReturn(List.of());
        when(ticketRepository.findClaimedTicketsFullFiltered(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));

        Page<Ticket> result = ticketService.getAgentClaimedTicketsFiltered("agent-1", f, p);
        assertNotNull(result);
    }

    @Test
    @DisplayName("getAgentClaimedTicketsFiltered → varsayılan path")
    void getAgentClaimedTicketsFiltered_default() {
        Pageable p = PageRequest.of(0, 10);
        when(ticketClaimRepository.findTicketIdsByAgentId("agent-1")).thenReturn(List.of(100L));
        when(ticketRepository.findClaimedTicketsFiltered(any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));

        Page<Ticket> result = ticketService.getAgentClaimedTicketsFiltered("agent-1", TicketFilterDTO.builder().build(), p);
        assertNotNull(result);
    }

    // =========================================================================
    // toNativePageable — switch case coverage
    // =========================================================================

    @Test
    @DisplayName("toNativePageable → resolvedAt → resolved_at")
    void getCustomerTicketsFiltered_sortByResolvedAt_fullFiltered() {
        Pageable p = PageRequest.of(0, 10, Sort.by(Sort.Direction.ASC, "resolvedAt"));
        TicketFilterDTO f = TicketFilterDTO.builder().search("x").build();
        when(productRepository.findAll()).thenReturn(List.of());
        when(ticketRepository.findByCustomerIdFullFiltered(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));

        Page<Ticket> result = ticketService.getCustomerTicketsFiltered("customer-1", f, p);
        assertNotNull(result);
    }

    @Test
    @DisplayName("toNativePageable → closedAt → closed_at")
    void getCustomerTicketsFiltered_sortByClosedAt_fullFiltered() {
        Pageable p = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "closedAt"));
        TicketFilterDTO f = TicketFilterDTO.builder().search("x").build();
        when(productRepository.findAll()).thenReturn(List.of());
        when(ticketRepository.findByCustomerIdFullFiltered(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));

        Page<Ticket> result = ticketService.getCustomerTicketsFiltered("customer-1", f, p);
        assertNotNull(result);
    }

    @Test
    @DisplayName("toNativePageable → slaBreached → sla_breached")
    void getCustomerTicketsFiltered_sortBySlaBreached_fullFiltered() {
        Pageable p = PageRequest.of(0, 10, Sort.by(Sort.Direction.ASC, "slaBreached"));
        TicketFilterDTO f = TicketFilterDTO.builder().search("x").build();
        when(productRepository.findAll()).thenReturn(List.of());
        when(ticketRepository.findByCustomerIdFullFiltered(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));

        Page<Ticket> result = ticketService.getCustomerTicketsFiltered("customer-1", f, p);
        assertNotNull(result);
    }

    @Test
    @DisplayName("toNativePageable → productId → product_id")
    void getCustomerTicketsFiltered_sortByProductId_fullFiltered() {
        Pageable p = PageRequest.of(0, 10, Sort.by(Sort.Direction.ASC, "productId"));
        TicketFilterDTO f = TicketFilterDTO.builder().search("x").build();
        when(productRepository.findAll()).thenReturn(List.of());
        when(ticketRepository.findByCustomerIdFullFiltered(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));

        Page<Ticket> result = ticketService.getCustomerTicketsFiltered("customer-1", f, p);
        assertNotNull(result);
    }

    @Test
    @DisplayName("toNativePageable → customerId → customer_id")
    void getCustomerTicketsFiltered_sortByCustomerId_fullFiltered() {
        Pageable p = PageRequest.of(0, 10, Sort.by(Sort.Direction.ASC, "customerId"));
        TicketFilterDTO f = TicketFilterDTO.builder().search("x").build();
        when(productRepository.findAll()).thenReturn(List.of());
        when(ticketRepository.findByCustomerIdFullFiltered(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));

        Page<Ticket> result = ticketService.getCustomerTicketsFiltered("customer-1", f, p);
        assertNotNull(result);
    }

    @Test
    @DisplayName("toNativePageable → bilinmeyen alan → default (alan adı aynen geçer)")
    void getCustomerTicketsFiltered_sortByUnknownField_fullFiltered() {
        Pageable p = PageRequest.of(0, 10, Sort.by(Sort.Direction.ASC, "assigneeId"));
        TicketFilterDTO f = TicketFilterDTO.builder().search("x").build();
        when(productRepository.findAll()).thenReturn(List.of());
        when(ticketRepository.findByCustomerIdFullFiltered(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));

        Page<Ticket> result = ticketService.getCustomerTicketsFiltered("customer-1", f, p);
        assertNotNull(result);
    }

    // =========================================================================
    // hasExtraFilters — per-condition coverage
    // =========================================================================

    @Test
    @DisplayName("hasExtraFilters → createdAtFrom koşulu tetikler")
    void getCustomerTicketsFiltered_createdAtFrom_triggersFullFiltered() {
        Pageable p = PageRequest.of(0, 10);
        TicketFilterDTO f = TicketFilterDTO.builder().createdAtFrom(ZonedDateTime.now().minusDays(7)).build();
        when(productRepository.findAll()).thenReturn(List.of());
        when(ticketRepository.findByCustomerIdFullFiltered(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));

        Page<Ticket> result = ticketService.getCustomerTicketsFiltered("customer-1", f, p);
        assertNotNull(result);
    }

    @Test
    @DisplayName("hasExtraFilters → createdAtTo koşulu tetikler")
    void getCustomerTicketsFiltered_createdAtTo_triggersFullFiltered() {
        Pageable p = PageRequest.of(0, 10);
        TicketFilterDTO f = TicketFilterDTO.builder().createdAtTo(ZonedDateTime.now()).build();
        when(productRepository.findAll()).thenReturn(List.of());
        when(ticketRepository.findByCustomerIdFullFiltered(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));

        Page<Ticket> result = ticketService.getCustomerTicketsFiltered("customer-1", f, p);
        assertNotNull(result);
    }

    @Test
    @DisplayName("hasExtraFilters → slaStatuses koşulu tetikler")
    void getCustomerTicketsFiltered_slaStatuses_triggersFullFiltered() {
        Pageable p = PageRequest.of(0, 10);
        TicketFilterDTO f = TicketFilterDTO.builder().slaStatuses(List.of("BREACHED")).build();
        when(productRepository.findAll()).thenReturn(List.of());
        when(ticketRepository.findByCustomerIdFullFiltered(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));

        Page<Ticket> result = ticketService.getCustomerTicketsFiltered("customer-1", f, p);
        assertNotNull(result);
    }

    @Test
    @DisplayName("hasExtraFilters → agentId koşulu tetikler")
    void getCustomerTicketsFiltered_agentId_triggersFullFiltered() {
        Pageable p = PageRequest.of(0, 10);
        TicketFilterDTO f = TicketFilterDTO.builder().agentId("agent-1").build();
        when(productRepository.findAll()).thenReturn(List.of());
        when(ticketRepository.findByCustomerIdFullFiltered(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));

        Page<Ticket> result = ticketService.getCustomerTicketsFiltered("customer-1", f, p);
        assertNotNull(result);
    }

    @Test
    @DisplayName("hasExtraFilters → productIds koşulu tetikler (findAll çağrılmaz)")
    void getCustomerTicketsFiltered_productIds_triggersFullFiltered() {
        Pageable p = PageRequest.of(0, 10);
        TicketFilterDTO f = TicketFilterDTO.builder().productIds(List.of(10L)).build();
        when(ticketRepository.findByCustomerIdFullFiltered(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));

        Page<Ticket> result = ticketService.getCustomerTicketsFiltered("customer-1", f, p);
        assertNotNull(result);
    }

    @Test
    @DisplayName("hasExtraFilters → çoklu statüs (size > 1) koşulu tetikler")
    void getCustomerTicketsFiltered_multiStatuses_triggersFullFiltered() {
        Pageable p = PageRequest.of(0, 10);
        TicketFilterDTO f = TicketFilterDTO.builder().statuses(List.of("NEW", "IN_PROGRESS")).build();
        when(productRepository.findAll()).thenReturn(List.of());
        when(ticketRepository.findByCustomerIdFullFiltered(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));

        Page<Ticket> result = ticketService.getCustomerTicketsFiltered("customer-1", f, p);
        assertNotNull(result);
    }

    @Test
    @DisplayName("hasExtraFilters → çoklu öncelik (size > 1) koşulu tetikler")
    void getCustomerTicketsFiltered_multiPriorities_triggersFullFiltered() {
        Pageable p = PageRequest.of(0, 10);
        TicketFilterDTO f = TicketFilterDTO.builder().priorities(List.of("HIGH", "CRITICAL")).build();
        when(productRepository.findAll()).thenReturn(List.of());
        when(ticketRepository.findByCustomerIdFullFiltered(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));

        Page<Ticket> result = ticketService.getCustomerTicketsFiltered("customer-1", f, p);
        assertNotNull(result);
    }

    @Test
    @DisplayName("hasExtraFilters → boş (blank) search → false döner, varsayılan path")
    void getCustomerTicketsFiltered_blankSearch_usesDefaultPath() {
        Pageable p = PageRequest.of(0, 10);
        TicketFilterDTO f = TicketFilterDTO.builder().search("   ").build();
        when(ticketRepository.findByCustomerIdFiltered(any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));

        Page<Ticket> result = ticketService.getCustomerTicketsFiltered("customer-1", f, p);
        assertNotNull(result);
    }

    @Test
    @DisplayName("TicketFilterDTO boş listeler → getter'larda !isEmpty() false branch'i")
    void getCustomerTicketsFiltered_emptyListFilters_usesDefaultPath() {
        Pageable p = PageRequest.of(0, 10);
        TicketFilterDTO f = TicketFilterDTO.builder()
                .statuses(List.of())
                .priorities(List.of())
                .slaStatuses(List.of())
                .productIds(List.of())
                .build();
        when(ticketRepository.findByCustomerIdFiltered(any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));

        Page<Ticket> result = ticketService.getCustomerTicketsFiltered("customer-1", f, p);
        assertNotNull(result);
    }
}

