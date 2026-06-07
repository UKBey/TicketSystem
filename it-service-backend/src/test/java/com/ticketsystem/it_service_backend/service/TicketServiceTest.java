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
import org.springframework.web.server.ResponseStatusException;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
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

    private TicketService ticketService;

    private Product product;
    private TicketTopic topic;
    private User customer;
    private User agent;
    private User agentAdmin;

    @BeforeEach
    void setUp() {
        TicketAuditHelper auditHelper = new TicketAuditHelper(ticketAuditLogRepository, messagingTemplate);
        TicketClaimService ticketClaimService = new TicketClaimService(
                ticketRepository, ticketClaimRepository, productRepository, userRepository,
                agentProductLimitRepository, workflowService, notificationService, auditHelper);
        ticketService = new TicketService(
                ticketRepository, ticketClaimRepository, productRepository, ticketTopicRepository,
                commentRepository, userRepository, workflowService, slaPolicyService,
                eventPublisher, csatRepository, worklogRepository, attachmentRepository,
                notificationService, auditHelper, ticketClaimService);

        // BPMN state machine'i artık authoritative validator; testler aksi belirtmedikçe
        // geçişi kabul ediyor varsayımıyla çalışsın. Spesifik bir geçişi reddetmek
        // isteyen test override eder.
        lenient().when(workflowService.verifyTransitionApplied(any(), any())).thenReturn(true);

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
                .role("ADMIN")
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

                List<Ticket> result = ticketService.getAllTickets("admin-1", List.of("ADMIN"));

                assertEquals(1, result.size());
                assertEquals(700L, result.get(0).getId());
        }

        @Test
        void getAllTickets_whenManager_returnsAllTickets() {
                Ticket ticket = Ticket.builder().id(701L).build();
                when(ticketRepository.findAll()).thenReturn(List.of(ticket));

                // MANAGER is now a global (read-only) role and sees every ticket.
                List<Ticket> result = ticketService.getAllTickets("admin-1", List.of("MANAGER"));

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

                List<Ticket> result = ticketService.getPoolTickets("admin-1", List.of("ADMIN"));

                assertEquals(1, result.size());
                assertEquals(702L, result.get(0).getId());
        }

        @Test
        void getPoolTickets_whenManager_returnsNewTickets() {
                Ticket ticket = Ticket.builder().id(703L).build();
                when(ticketRepository.findByStatus("NEW")).thenReturn(List.of(ticket));

                // MANAGER is now a global role; it sees the full NEW pool across all products.
                List<Ticket> result = ticketService.getPoolTickets("admin-1", List.of("MANAGER"));

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

                Ticket result = ticketService.getTicketWithAuth(705L, "admin-1", List.of("ADMIN"));

                assertEquals(705L, result.getId());
        }

        @Test
        void getTicketWithAuth_whenManager_returnsTicket() {
                Ticket ticket = Ticket.builder().id(704L).customerId("customer-1").productId(10L).build();
                when(ticketRepository.findById(704L)).thenReturn(Optional.of(ticket));

                // MANAGER has global read visibility — it sees any ticket without a product check.
                Ticket result = ticketService.getTicketWithAuth(704L, "admin-1", List.of("MANAGER"));

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
        void validateMutationAccess_whenAdmin_returnsTicketWithoutClaim() {
                Ticket ticket = Ticket.builder().id(707L).customerId("customer-1").productId(10L).build();
                when(ticketRepository.findById(707L)).thenReturn(Optional.of(ticket));

                // ADMIN acts globally and may mutate any ticket without holding a claim.
                Ticket result = ticketService.validateMutationAccess(707L, "admin-1", List.of("ADMIN"));

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
                when(ticketRepository.save(any(Ticket.class))).thenAnswer(invocation -> invocation.getArgument(0));

                Ticket updated = ticketService.updateTicketStatus(304L, "RESOLVED", "SOLUTION_PROVIDED", null, "agent-1", List.of("AGENT"));

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
                when(ticketRepository.findById(305L)).thenReturn(Optional.of(waiting));
                when(ticketRepository.save(any(Ticket.class))).thenAnswer(invocation -> invocation.getArgument(0));

                Ticket updated = ticketService.updateTicketStatus(305L, "IN_PROGRESS", null, null, "customer-1", List.of("CUSTOMER"));

                assertEquals("IN_PROGRESS", updated.getStatus());
                verify(workflowService).resumeSla(updated);
                verify(ticketRepository, times(2)).save(any(Ticket.class));
        }

    @Test
    void claimTicket_whenTicketClosed_throwsBadRequest() {
        Ticket existing = Ticket.builder()
                .id(201L)
                .title("Already closed")
                .description("desc")
                .priority("MEDIUM")
                .status("CLOSED")
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
        // BPMN state machine NEW → CLOSED'a izin vermez (NEW yalnız IN_PROGRESS ve
        // CLOSED'ı (terminate) tutar; bu test agent rolünden CLOSE girişimi yapıyor
        // ki bu hem permission hem state perspektifinden geçersiz). BPMN signal
        // dropped + verifyTransitionApplied=false → 400.
        Ticket existing = Ticket.builder()
                .id(301L)
                .title("Ticket")
                .description("desc")
                .priority("HIGH")
                .status("NEW")
                .productId(10L)
                .customerId("customer-1")
                .processInstanceId(7301L)
                .build();

        when(ticketRepository.findById(301L)).thenReturn(Optional.of(existing));
        when(workflowService.verifyTransitionApplied(any(), eq("CLOSED"))).thenReturn(false);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> ticketService.updateTicketStatus(301L, "CLOSED", "RESOLVED_CONFIRMED", null, "agent-1", List.of("AGENT")));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        verify(ticketRepository, never()).save(any(Ticket.class));
    }

    @Test
    void updateTicketStatus_whenProcessInstanceMissing_acceptsTransition() {
        // Stale processInstanceId: the BPMN instance was pruned/reset (e.g. jBPM
        // history store wiped) but the ticket survived in ticketdb. The transition
        // can't be confirmed (verifyTransitionApplied=false), yet the instance is
        // confirmed GONE — so the DB-side close is accepted instead of a 400
        // dead-end that would leave the ticket un-closeable forever.
        Ticket existing = Ticket.builder()
                .id(310L)
                .title("Ticket")
                .description("desc")
                .priority("HIGH")
                .status("RESOLVED")
                .productId(10L)
                .customerId("customer-1")
                .processInstanceId(9999L)
                .build();

        when(ticketRepository.findById(310L)).thenReturn(Optional.of(existing));
        when(ticketRepository.save(any(Ticket.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(workflowService.verifyTransitionApplied(any(), eq("CLOSED"))).thenReturn(false);
        when(workflowService.isProcessInstanceMissing(any())).thenReturn(true);

        Ticket updated = ticketService.updateTicketStatus(310L, "CLOSED", "RESOLVED_CONFIRMED", null, "customer-1", List.of("CUSTOMER"));

        assertEquals("CLOSED", updated.getStatus());
        assertNotNull(updated.getClosedAt());
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
                () -> ticketService.updateTicketStatus(302L, "IN_PROGRESS", null, null, "customer-1", List.of("CUSTOMER")));

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
                .processInstanceId(7601L)
                .build();

        when(ticketRepository.findById(601L)).thenReturn(Optional.of(existing));
        when(ticketClaimRepository.existsByTicketIdAndAgentId(601L, "agent-1")).thenReturn(true);
        when(ticketRepository.save(any(Ticket.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Ticket updated = ticketService.updateTicketStatus(601L, "IN_PROGRESS", null, null, "agent-1", List.of("AGENT"));

        assertEquals("IN_PROGRESS", updated.getStatus());
        assertNull(updated.getResolvedAt());
        // BPMN state branch'i status değişkenini kendi script task'ında günceller;
        // backend artık ayrıca syncTicketStatus çağırmıyor — onun yerine
        // requestStatusTransition + verifyTransitionApplied ile BPMN'i otoriter
        // validator olarak konuşuyor.
        verify(workflowService).requestStatusTransition(updated, "IN_PROGRESS");
        verify(workflowService).verifyTransitionApplied(updated, "IN_PROGRESS");
    }

    @Test
    void updateTicketStatus_whenCurrentStatusUnknown_throwsBadRequest() {
        // BPMN UNKNOWN_STATE adında bir state node tanımlamadığı için hiçbir
        // transition signal'i kabul edilmez → verifyTransitionApplied=false → 400.
        Ticket existing = Ticket.builder()
                .id(603L)
                .title("Ticket")
                .description("desc")
                .priority("HIGH")
                .status("UNKNOWN_STATE")
                .productId(10L)
                .customerId("customer-1")
                .processInstanceId(7603L)
                .build();

        when(ticketRepository.findById(603L)).thenReturn(Optional.of(existing));
        when(workflowService.verifyTransitionApplied(any(), eq("NEW"))).thenReturn(false);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> ticketService.updateTicketStatus(603L, "NEW", null, null, "agent-1", List.of("AGENT")));

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
                () -> ticketService.updateTicketStatus(604L, "CLOSED", "RESOLVED_CONFIRMED", null, "customer-1", List.of("CUSTOMER")));

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
                () -> ticketService.updateTicketStatus(605L, "NEW", null, null, "agent-1", List.of("AGENT")));

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
                .processInstanceId(7606L)
                .build();

        when(ticketRepository.findById(606L)).thenReturn(Optional.of(existing));
        when(ticketClaimRepository.existsByTicketIdAndAgentId(606L, "agent-1")).thenReturn(true);
        when(ticketRepository.save(any(Ticket.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Ticket updated = ticketService.updateTicketStatus(606L, "NEW", null, null, "agent-1", List.of("AGENT"));

        assertEquals("NEW", updated.getStatus());
        verify(ticketClaimRepository).deleteByTicketId(606L);
        verify(workflowService).requestStatusTransition(updated, "NEW");
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

        Ticket updated = ticketService.updateTicketStatus(607L, "CLOSED", "RESOLVED_CONFIRMED", null, "customer-1", List.of("CUSTOMER"));

        assertEquals("CLOSED", updated.getStatus());
        assertNotNull(updated.getClosedAt());
        verify(workflowService).closeTicketWorkflow(updated);
    }

    @Test
    void updateTicketStatus_whenWorkflowSlaSignalFails_stillUpdatesTicket() {
        // Statü geçişi doğrulaması başarılı (BPMN kabul etti) ama legacy SLA pause/
        // resume sinyali hata verdiğinde DB güncellemesi geri sarılmaz — SLA yan
        // etkileri best-effort olarak handleWorkflowSignals içinde try/catch ile
        // korunuyor.
        Ticket existing = Ticket.builder()
                .id(608L)
                .title("Ticket")
                .description("desc")
                .priority("HIGH")
                .status("IN_PROGRESS")
                .productId(10L)
                .customerId("customer-1")
                .processInstanceId(7608L)
                .build();

        when(ticketRepository.findById(608L)).thenReturn(Optional.of(existing));
        when(ticketClaimRepository.existsByTicketIdAndAgentId(608L, "agent-1")).thenReturn(true);
        when(ticketRepository.save(any(Ticket.class))).thenAnswer(invocation -> invocation.getArgument(0));
        doThrow(new RuntimeException("workflow unavailable"))
                .when(workflowService).pauseSla(any(Ticket.class));

        Ticket updated = ticketService.updateTicketStatus(608L, "WAITING_FOR_CUSTOMER", null, null, "agent-1", List.of("AGENT"));

        assertEquals("WAITING_FOR_CUSTOMER", updated.getStatus());
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
                () -> ticketService.unclaimTicket(800L, "agent-1", "WORKLOAD", null));

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

        Ticket result = ticketService.unclaimTicket(801L, "agent-1", "WORKLOAD", "giving up");

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

        Ticket result = ticketService.unclaimTicket(802L, "agent-1", "WORKLOAD", null);

        assertEquals("IN_PROGRESS", result.getStatus());
        verify(ticketRepository, never()).save(any(Ticket.class));
    }

    // =========================================================================
    // Non-paged list methods
    // =========================================================================

    @Test
    @DisplayName("getTeamTickets → ADMIN tüm ürünlere ait aktif biletleri döner")
    void getTeamTickets_agentAdmin_returnsActiveTickets() {
        Ticket t = Ticket.builder().id(900L).productId(10L).build();
        when(ticketRepository.findAllActive()).thenReturn(List.of(t));

        List<Ticket> result = ticketService.getTeamTickets("admin-1", List.of("ADMIN"));

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
        when(ticketRepository.findByCustomerIdFullFiltered(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
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
    // getPoolTicketsFiltered — ADMIN & AGENT branch coverage
    // =========================================================================

    @Test
    @DisplayName("getPoolTicketsFiltered → LEAD_AGENT priority ASC")
    void getPoolTicketsFiltered_agentAdmin_sortPriorityAsc() {
        Pageable p = PageRequest.of(0, 10, Sort.by(Sort.Direction.ASC, "priority"));
        when(userRepository.findById("admin-1")).thenReturn(Optional.of(agentAdmin));
        when(ticketRepository.findPoolTicketsFilteredOrderByPriorityAsc(any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));

        Page<Ticket> result = ticketService.getPoolTicketsFiltered("admin-1", List.of("LEAD_AGENT"), TicketFilterDTO.builder().build(), p);
        assertNotNull(result);
    }

    @Test
    @DisplayName("getPoolTicketsFiltered → LEAD_AGENT priority DESC")
    void getPoolTicketsFiltered_agentAdmin_sortPriorityDesc() {
        Pageable p = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "priority"));
        when(userRepository.findById("admin-1")).thenReturn(Optional.of(agentAdmin));
        when(ticketRepository.findPoolTicketsFilteredOrderByPriorityDesc(any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));

        Page<Ticket> result = ticketService.getPoolTicketsFiltered("admin-1", List.of("LEAD_AGENT"), TicketFilterDTO.builder().build(), p);
        assertNotNull(result);
    }

    @Test
    @DisplayName("getPoolTicketsFiltered → LEAD_AGENT SLA ASC")
    void getPoolTicketsFiltered_agentAdmin_sortSlaAsc() {
        Pageable p = PageRequest.of(0, 10, Sort.by(Sort.Direction.ASC, "slaDeadline"));
        when(userRepository.findById("admin-1")).thenReturn(Optional.of(agentAdmin));
        when(ticketRepository.findPoolTicketsFilteredOrderBySlaUrgencyAsc(any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));

        Page<Ticket> result = ticketService.getPoolTicketsFiltered("admin-1", List.of("LEAD_AGENT"), TicketFilterDTO.builder().build(), p);
        assertNotNull(result);
    }

    @Test
    @DisplayName("getPoolTicketsFiltered → LEAD_AGENT SLA DESC")
    void getPoolTicketsFiltered_agentAdmin_sortSlaDesc() {
        Pageable p = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "slaDeadline"));
        when(userRepository.findById("admin-1")).thenReturn(Optional.of(agentAdmin));
        when(ticketRepository.findPoolTicketsFilteredOrderBySlaUrgencyDesc(any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));

        Page<Ticket> result = ticketService.getPoolTicketsFiltered("admin-1", List.of("LEAD_AGENT"), TicketFilterDTO.builder().build(), p);
        assertNotNull(result);
    }

    @Test
    @DisplayName("getPoolTicketsFiltered → LEAD_AGENT full filtered")
    void getPoolTicketsFiltered_agentAdmin_withSearch() {
        Pageable p = PageRequest.of(0, 10);
        TicketFilterDTO f = TicketFilterDTO.builder().search("test").build();
        when(userRepository.findById("admin-1")).thenReturn(Optional.of(agentAdmin));
        when(productRepository.findAll()).thenReturn(List.of());
        when(ticketRepository.findPoolTicketsFullFiltered(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));

        Page<Ticket> result = ticketService.getPoolTicketsFiltered("admin-1", List.of("LEAD_AGENT"), f, p);
        assertNotNull(result);
    }

    @Test
    @DisplayName("getPoolTicketsFiltered → LEAD_AGENT default")
    void getPoolTicketsFiltered_agentAdmin_default() {
        Pageable p = PageRequest.of(0, 10);
        when(userRepository.findById("admin-1")).thenReturn(Optional.of(agentAdmin));
        when(ticketRepository.findPoolTicketsFiltered(any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));

        Page<Ticket> result = ticketService.getPoolTicketsFiltered("admin-1", List.of("LEAD_AGENT"), TicketFilterDTO.builder().build(), p);
        assertNotNull(result);
    }

    @Test
    @DisplayName("getPoolTicketsFiltered → ADMIN/global atanmış ürün olmadan tüm havuzu görür")
    void getPoolTicketsFiltered_global_seesAllProducts() {
        when(productRepository.findAll()).thenReturn(List.of(product));
        when(ticketRepository.findPoolTicketsFiltered(any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));

        Page<Ticket> result = ticketService.getPoolTicketsFiltered("admin-1", List.of("ADMIN"),
                TicketFilterDTO.builder().build(), PageRequest.of(0, 10));

        assertNotNull(result);
        verify(productRepository).findAll();
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
        when(ticketRepository.findPoolTicketsFullFiltered(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
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
    @DisplayName("getTeamTicketsFiltered → LEAD_AGENT priority ASC")
    void getTeamTicketsFiltered_agentAdmin_sortPriorityAsc() {
        Pageable p = PageRequest.of(0, 10, Sort.by(Sort.Direction.ASC, "priority"));
        when(userRepository.findById("admin-1")).thenReturn(Optional.of(agentAdmin));
        when(ticketRepository.findTeamTicketsFilteredOrderByPriorityAsc(any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));

        Page<Ticket> result = ticketService.getTeamTicketsFiltered("admin-1", List.of("LEAD_AGENT"), TicketFilterDTO.builder().build(), p);
        assertNotNull(result);
    }

    @Test
    @DisplayName("getTeamTicketsFiltered → LEAD_AGENT priority DESC")
    void getTeamTicketsFiltered_agentAdmin_sortPriorityDesc() {
        Pageable p = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "priority"));
        when(userRepository.findById("admin-1")).thenReturn(Optional.of(agentAdmin));
        when(ticketRepository.findTeamTicketsFilteredOrderByPriorityDesc(any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));

        Page<Ticket> result = ticketService.getTeamTicketsFiltered("admin-1", List.of("LEAD_AGENT"), TicketFilterDTO.builder().build(), p);
        assertNotNull(result);
    }

    @Test
    @DisplayName("getTeamTicketsFiltered → LEAD_AGENT SLA ASC")
    void getTeamTicketsFiltered_agentAdmin_sortSlaAsc() {
        Pageable p = PageRequest.of(0, 10, Sort.by(Sort.Direction.ASC, "slaDeadline"));
        when(userRepository.findById("admin-1")).thenReturn(Optional.of(agentAdmin));
        when(ticketRepository.findTeamTicketsFilteredOrderBySlaUrgencyAsc(any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));

        Page<Ticket> result = ticketService.getTeamTicketsFiltered("admin-1", List.of("LEAD_AGENT"), TicketFilterDTO.builder().build(), p);
        assertNotNull(result);
    }

    @Test
    @DisplayName("getTeamTicketsFiltered → LEAD_AGENT SLA DESC")
    void getTeamTicketsFiltered_agentAdmin_sortSlaDesc() {
        Pageable p = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "slaDeadline"));
        when(userRepository.findById("admin-1")).thenReturn(Optional.of(agentAdmin));
        when(ticketRepository.findTeamTicketsFilteredOrderBySlaUrgencyDesc(any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));

        Page<Ticket> result = ticketService.getTeamTicketsFiltered("admin-1", List.of("LEAD_AGENT"), TicketFilterDTO.builder().build(), p);
        assertNotNull(result);
    }

    @Test
    @DisplayName("getTeamTicketsFiltered → LEAD_AGENT full filtered")
    void getTeamTicketsFiltered_agentAdmin_withSearch() {
        Pageable p = PageRequest.of(0, 10);
        TicketFilterDTO f = TicketFilterDTO.builder().search("vpn").build();
        when(userRepository.findById("admin-1")).thenReturn(Optional.of(agentAdmin));
        when(productRepository.findAll()).thenReturn(List.of());
        when(ticketRepository.findTeamTicketsFullFiltered(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));

        Page<Ticket> result = ticketService.getTeamTicketsFiltered("admin-1", List.of("LEAD_AGENT"), f, p);
        assertNotNull(result);
    }

    @Test
    @DisplayName("getTeamTicketsFiltered → LEAD_AGENT default")
    void getTeamTicketsFiltered_agentAdmin_default() {
        Pageable p = PageRequest.of(0, 10);
        when(userRepository.findById("admin-1")).thenReturn(Optional.of(agentAdmin));
        when(ticketRepository.findTeamTicketsFiltered(any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));

        Page<Ticket> result = ticketService.getTeamTicketsFiltered("admin-1", List.of("LEAD_AGENT"), TicketFilterDTO.builder().build(), p);
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
        when(ticketRepository.findTeamTicketsFilteredOrderByPriorityAsc(any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));

        Page<Ticket> result = ticketService.getTeamTicketsFiltered("agent-1", List.of("AGENT"), TicketFilterDTO.builder().build(), p);
        assertNotNull(result);
    }

    @Test
    @DisplayName("getTeamTicketsFiltered → AGENT priority DESC")
    void getTeamTicketsFiltered_agent_sortPriorityDesc() {
        Pageable p = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "priority"));
        when(userRepository.findById("agent-1")).thenReturn(Optional.of(agent));
        when(ticketRepository.findTeamTicketsFilteredOrderByPriorityDesc(any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));

        Page<Ticket> result = ticketService.getTeamTicketsFiltered("agent-1", List.of("AGENT"), TicketFilterDTO.builder().build(), p);
        assertNotNull(result);
    }

    @Test
    @DisplayName("getTeamTicketsFiltered → AGENT SLA ASC")
    void getTeamTicketsFiltered_agent_sortSlaAsc() {
        Pageable p = PageRequest.of(0, 10, Sort.by(Sort.Direction.ASC, "slaDeadline"));
        when(userRepository.findById("agent-1")).thenReturn(Optional.of(agent));
        when(ticketRepository.findTeamTicketsFilteredOrderBySlaUrgencyAsc(any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));

        Page<Ticket> result = ticketService.getTeamTicketsFiltered("agent-1", List.of("AGENT"), TicketFilterDTO.builder().build(), p);
        assertNotNull(result);
    }

    @Test
    @DisplayName("getTeamTicketsFiltered → AGENT SLA DESC")
    void getTeamTicketsFiltered_agent_sortSlaDesc() {
        Pageable p = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "slaDeadline"));
        when(userRepository.findById("agent-1")).thenReturn(Optional.of(agent));
        when(ticketRepository.findTeamTicketsFilteredOrderBySlaUrgencyDesc(any(), any(), any(), any()))
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
        when(ticketRepository.findTeamTicketsFullFiltered(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));

        Page<Ticket> result = ticketService.getTeamTicketsFiltered("agent-1", List.of("AGENT"), f, p);
        assertNotNull(result);
    }

    @Test
    @DisplayName("getTeamTicketsFiltered → AGENT default")
    void getTeamTicketsFiltered_agent_default() {
        Pageable p = PageRequest.of(0, 10);
        when(userRepository.findById("agent-1")).thenReturn(Optional.of(agent));
        when(ticketRepository.findTeamTicketsFiltered(any(), any(), any(), any()))
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
        when(ticketRepository.findByProductIdFullFiltered(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
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
        when(ticketRepository.findByProductIdAndCustomerIdFullFiltered(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
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
        when(ticketRepository.findClaimedTicketsFullFiltered(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
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
        when(ticketRepository.findByCustomerIdFullFiltered(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
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
        when(ticketRepository.findByCustomerIdFullFiltered(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
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
        when(ticketRepository.findByCustomerIdFullFiltered(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
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
        when(ticketRepository.findByCustomerIdFullFiltered(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
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
        when(ticketRepository.findByCustomerIdFullFiltered(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
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
        when(ticketRepository.findByCustomerIdFullFiltered(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
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
        when(ticketRepository.findByCustomerIdFullFiltered(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
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
        when(ticketRepository.findByCustomerIdFullFiltered(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
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
        when(ticketRepository.findByCustomerIdFullFiltered(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));

        Page<Ticket> result = ticketService.getCustomerTicketsFiltered("customer-1", f, p);
        assertNotNull(result);
    }

    @Test
    @DisplayName("hasExtraFilters → agentIds koşulu tetikler")
    void getCustomerTicketsFiltered_agentId_triggersFullFiltered() {
        Pageable p = PageRequest.of(0, 10);
        TicketFilterDTO f = TicketFilterDTO.builder().agentIds(List.of("agent-1")).build();
        when(productRepository.findAll()).thenReturn(List.of());
        when(ticketRepository.findByCustomerIdFullFiltered(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));

        Page<Ticket> result = ticketService.getCustomerTicketsFiltered("customer-1", f, p);
        assertNotNull(result);
    }

    @Test
    @DisplayName("hasExtraFilters → productIds koşulu tetikler (findAll çağrılmaz)")
    void getCustomerTicketsFiltered_productIds_triggersFullFiltered() {
        Pageable p = PageRequest.of(0, 10);
        TicketFilterDTO f = TicketFilterDTO.builder().productIds(List.of(10L)).build();
        when(ticketRepository.findByCustomerIdFullFiltered(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
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
        when(ticketRepository.findByCustomerIdFullFiltered(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
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
        when(ticketRepository.findByCustomerIdFullFiltered(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
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

    // -------------------------------------------------------------------------
    // *Paged delegation methods — wrapper for *Filtered with simple DTO build
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("getCustomerTicketsPaged → null/blank → empty filter list yapımı")
    void getCustomerTicketsPaged_nullParams_buildsEmptyFilter() {
        Pageable p = PageRequest.of(0, 10);
        when(ticketRepository.findByCustomerIdFiltered(eq("c-1"), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));

        Page<Ticket> result = ticketService.getCustomerTicketsPaged("c-1", null, null, p);
        assertNotNull(result);
    }

    @Test
    @DisplayName("getCustomerTicketsPaged → status+priority → DTO listelere dönüşür")
    void getCustomerTicketsPaged_withValues_buildsListsAndDelegates() {
        Pageable p = PageRequest.of(0, 10);
        when(ticketRepository.findByCustomerIdFiltered(eq("c-1"), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));

        Page<Ticket> result = ticketService.getCustomerTicketsPaged("c-1", "NEW", "HIGH", p);
        assertNotNull(result);
    }

    @Test
    @DisplayName("getPoolTicketsPaged → null priority → empty filter")
    void getPoolTicketsPaged_nullPriority_buildsEmpty() {
        Pageable p = PageRequest.of(0, 10);
        when(userRepository.findById("u-1")).thenReturn(Optional.of(
                User.builder().id("u-1").authorizedProducts(List.of(product)).build()));
        when(ticketRepository.findPoolTicketsFiltered(any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));

        Page<Ticket> result = ticketService.getPoolTicketsPaged("u-1", List.of("AGENT"), null, p);
        assertNotNull(result);
    }

    @Test
    @DisplayName("getAgentClaimedTicketsPaged → priority empty → null filter list")
    void getAgentClaimedTicketsPaged_blankParams() {
        Pageable p = PageRequest.of(0, 10);
        when(ticketClaimRepository.findTicketIdsByAgentId("a-1")).thenReturn(List.of(1L));
        when(ticketRepository.findClaimedTicketsFiltered(eq(List.of(1L)), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));

        Page<Ticket> result = ticketService.getAgentClaimedTicketsPaged("a-1", "  ", "  ", p);
        assertNotNull(result);
    }

    @Test
    @DisplayName("getTeamTicketsPaged → null userId → empty page")
    void getTeamTicketsPaged_nullUser_empty() {
        Page<Ticket> result = ticketService.getTeamTicketsPaged(null, List.of("AGENT"), null, PageRequest.of(0, 10));
        assertNotNull(result);
    }

    @Test
    @DisplayName("getTicketsByProductPaged → status+priority null → filter listelere null koyar")
    void getTicketsByProductPaged_nullArgs_buildsEmpty() {
        Pageable p = PageRequest.of(0, 10);
        when(userRepository.findById("agent-1")).thenReturn(Optional.of(
                User.builder().id("agent-1").authorizedProducts(List.of(product)).build()));
        when(ticketRepository.findByProductIdFiltered(eq(10L), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));

        Page<Ticket> result = ticketService.getTicketsByProductPaged(10L, "agent-1", List.of("AGENT"), null, null, p);
        assertNotNull(result);
    }

    // -------------------------------------------------------------------------
    // updateTicketPriority — biggest single uncovered method (~142 instr)
    // -------------------------------------------------------------------------

    private Ticket inProgressTicketForPriority() {
        return Ticket.builder()
                .id(950L)
                .title("t").description("d")
                .priority("LOW").status("IN_PROGRESS")
                .productId(10L).customerId("customer-1")
                .slaBreached(false).slaElapsedMs(0L)
                .createdAt(ZonedDateTime.now())
                .build();
    }

    @Test
    @DisplayName("updateTicketPriority → geçersiz priority → BAD_REQUEST")
    void updateTicketPriority_invalid_throwsBadRequest() {
        assertThrows(ResponseStatusException.class,
                () -> ticketService.updateTicketPriority(950L, "URGENT", "CUSTOMER_IMPACT", null, "agent-1", List.of("AGENT")));
    }

    @Test
    @DisplayName("updateTicketPriority → aynı priority → değişiklik yapılmaz")
    void updateTicketPriority_samePriority_noChange() {
        Ticket existing = inProgressTicketForPriority();
        when(ticketRepository.findById(950L)).thenReturn(Optional.of(existing));
        when(userRepository.findById("agent-1")).thenReturn(Optional.of(agent));

        Ticket result = ticketService.updateTicketPriority(950L, "LOW", "CUSTOMER_IMPACT", null, "agent-1", List.of("AGENT"));

        assertEquals("LOW", result.getPriority());
        verify(ticketRepository, never()).save(any(Ticket.class));
    }

    @Test
    @DisplayName("updateTicketPriority → aktif SLA, duraklatılmamış → pauseSla + resumeSla çağrılır")
    void updateTicketPriority_activeSla_pausesAndResumes() {
        Ticket existing = inProgressTicketForPriority();
        when(ticketRepository.findById(950L)).thenReturn(Optional.of(existing));
        when(userRepository.findById("agent-1")).thenReturn(Optional.of(agent));
        when(slaPolicyService.getSlaDurationMs("HIGH")).thenReturn(14_400_000L);
        when(ticketRepository.save(any(Ticket.class))).thenAnswer(i -> i.getArgument(0));

        Ticket result = ticketService.updateTicketPriority(950L, "HIGH", "CUSTOMER_IMPACT", null, "agent-1", List.of("AGENT"));

        assertEquals("HIGH", result.getPriority());
        verify(workflowService).pauseSla(existing);
        verify(workflowService).resumeSla(existing);
    }

    @Test
    @DisplayName("updateTicketPriority → WAITING_FOR_CUSTOMER → pauseSla atlanır, deadline yeniden hesaplanır")
    void updateTicketPriority_waitingForCustomer_pausedPath() {
        Ticket existing = inProgressTicketForPriority();
        existing.setStatus("WAITING_FOR_CUSTOMER");
        when(ticketRepository.findById(950L)).thenReturn(Optional.of(existing));
        when(userRepository.findById("agent-1")).thenReturn(Optional.of(agent));
        when(slaPolicyService.getSlaDurationMs("HIGH")).thenReturn(7_200_000L);
        when(ticketRepository.save(any(Ticket.class))).thenAnswer(i -> i.getArgument(0));

        Ticket result = ticketService.updateTicketPriority(950L, "HIGH", "CUSTOMER_IMPACT", null, "agent-1", List.of("AGENT"));

        assertEquals("HIGH", result.getPriority());
        verify(workflowService, never()).pauseSla(any());
        verify(workflowService, never()).resumeSla(any());
        assertNotNull(result.getSlaDeadline());
    }

    @Test
    @DisplayName("updateTicketPriority → CLOSED → SLA hesabı yapılmaz")
    void updateTicketPriority_closed_noSlaWork() {
        Ticket existing = inProgressTicketForPriority();
        existing.setStatus("CLOSED");
        when(ticketRepository.findById(950L)).thenReturn(Optional.of(existing));
        when(userRepository.findById("agent-1")).thenReturn(Optional.of(agent));
        when(ticketRepository.save(any(Ticket.class))).thenAnswer(i -> i.getArgument(0));

        Ticket result = ticketService.updateTicketPriority(950L, "HIGH", "CUSTOMER_IMPACT", null, "agent-1", List.of("AGENT"));

        assertEquals("HIGH", result.getPriority());
        verify(workflowService, never()).pauseSla(any());
    }

    @Test
    @DisplayName("updateTicketPriority → SLA breached → pauseSla atlanır")
    void updateTicketPriority_slaBreached_noPause() {
        Ticket existing = inProgressTicketForPriority();
        existing.setSlaBreached(true);
        when(ticketRepository.findById(950L)).thenReturn(Optional.of(existing));
        when(userRepository.findById("agent-1")).thenReturn(Optional.of(agent));
        when(ticketRepository.save(any(Ticket.class))).thenAnswer(i -> i.getArgument(0));

        Ticket result = ticketService.updateTicketPriority(950L, "HIGH", "CUSTOMER_IMPACT", null, "agent-1", List.of("AGENT"));

        assertEquals("HIGH", result.getPriority());
        verify(workflowService, never()).pauseSla(any());
    }

    // -------------------------------------------------------------------------
    // hasExtraFilters branch coverage
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("hasExtraFilters → tek priority → no extra → kısa yol")
    void getCustomerTicketsFiltered_singlePriorityNoExtra_skipsFullFiltered() {
        Pageable p = PageRequest.of(0, 10);
        TicketFilterDTO f = TicketFilterDTO.builder().priorities(List.of("HIGH")).build();
        when(ticketRepository.findByCustomerIdFiltered(eq("c-1"), any(), eq(List.of("HIGH")), any()))
                .thenReturn(new PageImpl<>(List.of()));

        Page<Ticket> result = ticketService.getCustomerTicketsFiltered("c-1", f, p);
        assertNotNull(result);
    }

    @Test
    @DisplayName("hasExtraFilters → çoklu priority → fullFiltered tetiklenir")
    void getCustomerTicketsFiltered_multiPriority_usesFullFiltered() {
        Pageable p = PageRequest.of(0, 10);
        TicketFilterDTO f = TicketFilterDTO.builder().priorities(List.of("HIGH", "MEDIUM")).build();
        when(productRepository.findAll()).thenReturn(List.of());
        when(ticketRepository.findByCustomerIdFullFiltered(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));

        Page<Ticket> result = ticketService.getCustomerTicketsFiltered("c-1", f, p);
        assertNotNull(result);
    }

    // ------------------------------------------------------------------------
    // hasExtraFilters individual flags
    // ------------------------------------------------------------------------

    @Test
    void hasExtraFilters_dateFrom_triggersFull() {
        TicketFilterDTO f = TicketFilterDTO.builder().createdAtFrom(ZonedDateTime.now()).build();
        when(productRepository.findAll()).thenReturn(List.of());
        when(ticketRepository.findByCustomerIdFullFiltered(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));
        ticketService.getCustomerTicketsFiltered("c-1", f, PageRequest.of(0, 10));
        org.mockito.Mockito.verify(ticketRepository).findByCustomerIdFullFiltered(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void hasExtraFilters_dateTo_triggersFull() {
        TicketFilterDTO f = TicketFilterDTO.builder().createdAtTo(ZonedDateTime.now()).build();
        when(productRepository.findAll()).thenReturn(List.of());
        when(ticketRepository.findByCustomerIdFullFiltered(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));
        ticketService.getCustomerTicketsFiltered("c-1", f, PageRequest.of(0, 10));
        org.mockito.Mockito.verify(ticketRepository).findByCustomerIdFullFiltered(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void hasExtraFilters_slaStatuses_triggersFull() {
        TicketFilterDTO f = TicketFilterDTO.builder().slaStatuses(List.of("BREACHED")).build();
        when(productRepository.findAll()).thenReturn(List.of());
        when(ticketRepository.findByCustomerIdFullFiltered(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));
        ticketService.getCustomerTicketsFiltered("c-1", f, PageRequest.of(0, 10));
        org.mockito.Mockito.verify(ticketRepository).findByCustomerIdFullFiltered(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void hasExtraFilters_agentIds_triggersFull() {
        TicketFilterDTO f = TicketFilterDTO.builder().agentIds(List.of("a-1")).build();
        when(productRepository.findAll()).thenReturn(List.of());
        when(ticketRepository.findByCustomerIdFullFiltered(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));
        ticketService.getCustomerTicketsFiltered("c-1", f, PageRequest.of(0, 10));
        org.mockito.Mockito.verify(ticketRepository).findByCustomerIdFullFiltered(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void hasExtraFilters_topicIds_triggersFull() {
        TicketFilterDTO f = TicketFilterDTO.builder().topicIds(List.of(5L)).build();
        when(productRepository.findAll()).thenReturn(List.of());
        when(ticketRepository.findByCustomerIdFullFiltered(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));
        ticketService.getCustomerTicketsFiltered("c-1", f, PageRequest.of(0, 10));
        org.mockito.Mockito.verify(ticketRepository).findByCustomerIdFullFiltered(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void hasExtraFilters_productIds_triggersFull() {
        TicketFilterDTO f = TicketFilterDTO.builder().productIds(List.of(10L)).build();
        when(ticketRepository.findByCustomerIdFullFiltered(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));
        ticketService.getCustomerTicketsFiltered("c-1", f, PageRequest.of(0, 10));
        org.mockito.Mockito.verify(ticketRepository).findByCustomerIdFullFiltered(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void hasExtraFilters_multipleStatuses_triggersFull() {
        TicketFilterDTO f = TicketFilterDTO.builder().statuses(List.of("NEW", "IN_PROGRESS")).build();
        when(productRepository.findAll()).thenReturn(List.of());
        when(ticketRepository.findByCustomerIdFullFiltered(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));
        ticketService.getCustomerTicketsFiltered("c-1", f, PageRequest.of(0, 10));
        org.mockito.Mockito.verify(ticketRepository).findByCustomerIdFullFiltered(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    // ------------------------------------------------------------------------
    // validateReasonInput edge cases through closeTicket
    // ------------------------------------------------------------------------

    @Test
    void closeTicket_otherReasonNoNote_throwsBadRequest() {
        Ticket existing = Ticket.builder().id(1900L).status("IN_PROGRESS").customerId("c-1").productId(10L).build();
        when(ticketRepository.findById(1900L)).thenReturn(Optional.of(existing));
        when(ticketClaimRepository.existsByTicketIdAndAgentId(1900L, "agent-1")).thenReturn(true);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> ticketService.closeTicket(1900L, "OTHER", "  ", "agent-1", List.of("AGENT")));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    void closeTicket_otherReasonWithNote_proceedsToClose() {
        Ticket existing = Ticket.builder().id(1901L).status("IN_PROGRESS").customerId("c-1").productId(10L).build();
        when(ticketRepository.findById(1901L)).thenReturn(Optional.of(existing));
        when(ticketClaimRepository.existsByTicketIdAndAgentId(1901L, "agent-1")).thenReturn(true);
        when(ticketRepository.save(any(Ticket.class))).thenAnswer(i -> i.getArgument(0));

        Ticket result = ticketService.closeTicket(1901L, "OTHER", "explanation", "agent-1", List.of("AGENT"));
        assertEquals("CLOSED", result.getStatus());
    }

    // ------------------------------------------------------------------------
    // getTicketWithAuth / validateMutationAccess extra paths
    // ------------------------------------------------------------------------

    @Test
    void getTicketWithAuth_customerOwnsTicket_returns() {
        Ticket existing = Ticket.builder().id(2000L).status("NEW").customerId("customer-1").productId(10L).build();
        when(ticketRepository.findById(2000L)).thenReturn(Optional.of(existing));

        Ticket result = ticketService.getTicketWithAuth(2000L, "customer-1", List.of("CUSTOMER"));
        assertEquals(2000L, result.getId());
    }

    @Test
    void getTicketWithAuth_anonymous_throwsForbidden() {
        Ticket existing = Ticket.builder().id(2001L).status("NEW").customerId("c-1").productId(10L).build();
        when(ticketRepository.findById(2001L)).thenReturn(Optional.of(existing));

        assertThrows(ResponseStatusException.class,
                () -> ticketService.getTicketWithAuth(2001L, "stranger", List.of()));
    }

    @Test
    void validateMutationAccess_customerOwnsTicket_returns() {
        Ticket existing = Ticket.builder().id(2100L).status("NEW").customerId("c-1").productId(10L).build();
        when(ticketRepository.findById(2100L)).thenReturn(Optional.of(existing));

        Ticket result = ticketService.validateMutationAccess(2100L, "c-1", List.of("CUSTOMER"));
        assertEquals(2100L, result.getId());
    }

    @Test
    void validateMutationAccess_otherCustomer_throwsForbidden() {
        Ticket existing = Ticket.builder().id(2101L).status("NEW").customerId("c-1").productId(10L).build();
        when(ticketRepository.findById(2101L)).thenReturn(Optional.of(existing));

        assertThrows(ResponseStatusException.class,
                () -> ticketService.validateMutationAccess(2101L, "other", List.of("CUSTOMER")));
    }

    @Test
    void validateMutationAccess_agentNotClaimer_throwsForbidden() {
        Ticket existing = Ticket.builder().id(2102L).status("NEW").customerId("c-1").productId(10L).build();
        when(ticketRepository.findById(2102L)).thenReturn(Optional.of(existing));
        when(ticketClaimRepository.existsByTicketIdAndAgentId(2102L, "agent-x")).thenReturn(false);

        assertThrows(ResponseStatusException.class,
                () -> ticketService.validateMutationAccess(2102L, "agent-x", List.of("AGENT")));
    }

    // ------------------------------------------------------------------------
    // claimTicket — remaining branches
    // ------------------------------------------------------------------------

    @Test
    void claimTicket_waitingForCustomerStatus_addsClaim() {
        Ticket existing = Ticket.builder().id(2200L).status("WAITING_FOR_CUSTOMER")
                .priority("HIGH").productId(10L).customerId("c-1").build();
        when(ticketRepository.findById(2200L)).thenReturn(Optional.of(existing));
        when(userRepository.findById("agent-1")).thenReturn(Optional.of(agent));
        when(productRepository.findById(10L)).thenReturn(Optional.of(product));
        when(ticketClaimRepository.existsByTicketIdAndAgentId(2200L, "agent-1")).thenReturn(false);

        Ticket result = ticketService.claimTicket(2200L, "agent-1");

        assertEquals("WAITING_FOR_CUSTOMER", result.getStatus());
        verify(ticketRepository, never()).save(any(Ticket.class));
    }

    @Test
    void claimTicket_alreadyClaimed_throwsConflict() {
        Ticket existing = Ticket.builder().id(2201L).status("IN_PROGRESS")
                .priority("HIGH").productId(10L).customerId("c-1").build();
        when(ticketRepository.findById(2201L)).thenReturn(Optional.of(existing));
        when(userRepository.findById("agent-1")).thenReturn(Optional.of(agent));
        when(productRepository.findById(10L)).thenReturn(Optional.of(product));
        when(ticketClaimRepository.existsByTicketIdAndAgentId(2201L, "agent-1")).thenReturn(true);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> ticketService.claimTicket(2201L, "agent-1"));
        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
    }

    @Test
    void claimTicket_customLimitDisabled_usesProductLimit() {
        Ticket existing = Ticket.builder().id(2202L).status("NEW")
                .priority("HIGH").productId(10L).customerId("c-1").build();
        Product productWithLimit = Product.builder().id(10L).name("X").maxActiveTickets(5).build();
        when(ticketRepository.findById(2202L)).thenReturn(Optional.of(existing));
        when(userRepository.findById("agent-1")).thenReturn(Optional.of(agent));
        when(productRepository.findById(10L)).thenReturn(Optional.of(productWithLimit));
        AgentProductLimit override = AgentProductLimit.builder()
                .agentId("agent-1").product(productWithLimit).useCustomLimit(false).maxActiveTickets(2).build();
        when(agentProductLimitRepository.findByAgentIdAndProductId("agent-1", 10L))
                .thenReturn(Optional.of(override));
        when(ticketClaimRepository.countActiveTicketsByAgentAndProduct("agent-1", 10L)).thenReturn(1L);
        when(ticketClaimRepository.existsByTicketIdAndAgentId(2202L, "agent-1")).thenReturn(false);
        when(ticketRepository.save(any(Ticket.class))).thenAnswer(i -> i.getArgument(0));

        Ticket result = ticketService.claimTicket(2202L, "agent-1");
        assertEquals("IN_PROGRESS", result.getStatus());
    }

    @Test
    void claimTicket_workflowSyncFails_swallowsAndContinues() {
        Ticket existing = Ticket.builder().id(2203L).status("NEW")
                .priority("HIGH").productId(10L).customerId("c-1").processInstanceId(999L).build();
        when(ticketRepository.findById(2203L)).thenReturn(Optional.of(existing));
        when(userRepository.findById("agent-1")).thenReturn(Optional.of(agent));
        when(productRepository.findById(10L)).thenReturn(Optional.of(product));
        when(ticketClaimRepository.existsByTicketIdAndAgentId(2203L, "agent-1")).thenReturn(false);
        when(ticketRepository.save(any(Ticket.class))).thenAnswer(i -> i.getArgument(0));
        org.mockito.Mockito.doThrow(new RuntimeException("workflow down"))
                .when(workflowService).syncTicketAssignment(existing, "agent-1");

        Ticket result = ticketService.claimTicket(2203L, "agent-1");
        assertEquals("IN_PROGRESS", result.getStatus());
    }

    @Test
    void claimTicket_productNotFound_throwsNotFound() {
        Ticket existing = Ticket.builder().id(2204L).status("NEW")
                .priority("HIGH").productId(99L).customerId("c-1").build();
        when(ticketRepository.findById(2204L)).thenReturn(Optional.of(existing));
        User agentWithProduct = User.builder().id("agent-1")
                .authorizedProducts(List.of(Product.builder().id(99L).build()))
                .build();
        when(userRepository.findById("agent-1")).thenReturn(Optional.of(agentWithProduct));
        when(productRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResponseStatusException.class,
                () -> ticketService.claimTicket(2204L, "agent-1"));
    }

    // ------------------------------------------------------------------------
    // validateStatusChangePermission via updateTicketStatus
    // ------------------------------------------------------------------------

    @Test
    void updateTicketStatus_customerDisallowedTransition_throwsForbidden() {
        Ticket existing = Ticket.builder().id(2301L).status("NEW").customerId("c-1").productId(10L).build();
        when(ticketRepository.findById(2301L)).thenReturn(Optional.of(existing));

        assertThrows(ResponseStatusException.class,
                () -> ticketService.updateTicketStatus(2301L, "IN_PROGRESS", null, null, "c-1", List.of("CUSTOMER")));
    }

    @Test
    void updateTicketStatus_emptyRoles_throwsForbidden() {
        Ticket existing = Ticket.builder().id(2302L).status("IN_PROGRESS")
                .customerId("c-1").productId(10L).build();
        when(ticketRepository.findById(2302L)).thenReturn(Optional.of(existing));

        assertThrows(ResponseStatusException.class,
                () -> ticketService.updateTicketStatus(2302L, "NEW", null, null, "ghost", List.of()));
    }

    @Test
    void updateTicketStatus_agentWithoutClaim_throwsForbidden() {
        Ticket existing = Ticket.builder().id(2303L).status("IN_PROGRESS")
                .customerId("c-1").productId(10L).build();
        when(ticketRepository.findById(2303L)).thenReturn(Optional.of(existing));
        when(ticketClaimRepository.existsByTicketIdAndAgentId(2303L, "agent-1")).thenReturn(false);

        // A plain AGENT must hold a claim to change status; without one it is forbidden.
        assertThrows(ResponseStatusException.class,
                () -> ticketService.updateTicketStatus(2303L, "WAITING_FOR_CUSTOMER", null, null, "agent-1", List.of("AGENT")));
    }

    @Test
    void closeTicket_blankReasonCode_throwsBadRequest() {
        Ticket existing = Ticket.builder().id(2400L).status("IN_PROGRESS")
                .customerId("c-1").productId(10L).build();
        when(ticketRepository.findById(2400L)).thenReturn(Optional.of(existing));
        when(ticketClaimRepository.existsByTicketIdAndAgentId(2400L, "agent-1")).thenReturn(true);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> ticketService.closeTicket(2400L, "  ", "note", "agent-1", List.of("AGENT")));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    void createTicket_topicIdNull_whenProductHasActiveTopics_throwsBadRequest() {
        Ticket input = Ticket.builder().title("t").description("d").priority("HIGH")
                .productId(10L).build();
        when(userRepository.findById("customer-1")).thenReturn(Optional.of(customer));
        when(ticketTopicRepository.findByProductIdAndIsActiveTrueOrderByNameAsc(10L))
                .thenReturn(List.of(topic));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> ticketService.createTicket(input, "customer-1"));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        verify(ticketRepository, never()).save(any(Ticket.class));
    }

    @Test
    void createTicket_topicIdNull_whenNoActiveTopics_savesTopiclessTicket() {
        Ticket input = Ticket.builder().title("t").description("d").priority("HIGH")
                .productId(10L).build();
        when(userRepository.findById("customer-1")).thenReturn(Optional.of(customer));
        when(ticketTopicRepository.findByProductIdAndIsActiveTrueOrderByNameAsc(10L))
                .thenReturn(List.of());
        when(slaPolicyService.getSlaDurationMs("HIGH")).thenReturn(14_400_000L);
        when(ticketRepository.save(any(Ticket.class))).thenAnswer(invocation -> {
            Ticket toSave = invocation.getArgument(0);
            toSave.setId(123L);
            return toSave;
        });

        Ticket saved = ticketService.createTicket(input, "customer-1");

        assertNotNull(saved.getId());
        assertEquals("NEW", saved.getStatus());
        assertNull(saved.getTopicId());
        assertNull(saved.getTopicNameSnapshot());
        verify(ticketTopicRepository, never()).findById(any());
    }

    @Test
    void updateTicketTopic_updatesTopicIdAndNameSnapshot() {
        Ticket existing = Ticket.builder()
                .id(700L).productId(10L).customerId("customer-1")
                .title("t").description("d").priority("HIGH").status("IN_PROGRESS")
                .build(); // topicless ticket (topicId == null)
        when(ticketRepository.findById(700L)).thenReturn(Optional.of(existing));
        when(userRepository.findById("agent-1")).thenReturn(Optional.of(agent));
        when(ticketTopicRepository.findById(50L)).thenReturn(Optional.of(topic));
        when(ticketRepository.save(any(Ticket.class))).thenAnswer(inv -> inv.getArgument(0));

        Ticket saved = ticketService.updateTicketTopic(
                700L, 50L, "MISCATEGORIZED", null, "agent-1", List.of("AGENT"));

        assertEquals(50L, saved.getTopicId());
        // Regression: the name snapshot must follow the new topic, otherwise the
        // response shows "#<id>" instead of the topic name.
        assertEquals("Diğer", saved.getTopicNameSnapshot());
    }

    @Test
    void createTicket_topicNotFound_throwsNotFound() {
        Ticket input = Ticket.builder().title("t").description("d").priority("HIGH")
                .productId(10L).topicId(99L).build();
        when(userRepository.findById("customer-1")).thenReturn(Optional.of(customer));
        when(ticketTopicRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResponseStatusException.class,
                () -> ticketService.createTicket(input, "customer-1"));
    }

    @Test
    void createTicket_topicProductMismatch_throwsBadRequest() {
        Ticket input = Ticket.builder().title("t").description("d").priority("HIGH")
                .productId(10L).topicId(20L).build();
        when(userRepository.findById("customer-1")).thenReturn(Optional.of(customer));
        when(ticketTopicRepository.findById(20L)).thenReturn(Optional.of(
                com.ticketsystem.it_service_backend.entity.TicketTopic.builder()
                        .id(20L).productId(999L).name("Other").isActive(true).build()));

        assertThrows(ResponseStatusException.class,
                () -> ticketService.createTicket(input, "customer-1"));
    }

    @Test
    void createTicket_topicInactive_throwsUnprocessable() {
        Ticket input = Ticket.builder().title("t").description("d").priority("HIGH")
                .productId(10L).topicId(20L).build();
        when(userRepository.findById("customer-1")).thenReturn(Optional.of(customer));
        when(ticketTopicRepository.findById(20L)).thenReturn(Optional.of(
                com.ticketsystem.it_service_backend.entity.TicketTopic.builder()
                        .id(20L).productId(10L).name("Inactive").isActive(false).build()));

        assertThrows(ResponseStatusException.class,
                () -> ticketService.createTicket(input, "customer-1"));
    }

    @Test
    void createTicket_productInactive_throws422() {
        Product inactive = Product.builder().id(10L).name("X").isActive(false).build();
        User c = User.builder().id("customer-1")
                .authorizedProducts(List.of(inactive)).build();
        Ticket input = Ticket.builder().title("t").description("d").priority("HIGH")
                .productId(10L).topicId(20L).build();
        when(userRepository.findById("customer-1")).thenReturn(Optional.of(c));

        assertThrows(ResponseStatusException.class,
                () -> ticketService.createTicket(input, "customer-1"));
    }

    @Test
    void getCustomerTicketsPaged_blankParams_buildsEmptyFilter() {
        when(ticketRepository.findByCustomerIdFiltered(eq("c-1"), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));
        ticketService.getCustomerTicketsPaged("c-1", "  ", "  ", PageRequest.of(0, 10));
        org.mockito.Mockito.verify(ticketRepository).findByCustomerIdFiltered(eq("c-1"), any(), any(), any());
    }

    @Test
    void getPoolTicketsPaged_blankPriority_buildsEmptyFilter() {
        when(userRepository.findById("u-1")).thenReturn(Optional.of(
                User.builder().id("u-1").authorizedProducts(List.of(product)).build()));
        when(ticketRepository.findPoolTicketsFiltered(any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));
        ticketService.getPoolTicketsPaged("u-1", List.of("AGENT"), "  ", PageRequest.of(0, 10));
        org.mockito.Mockito.verify(ticketRepository).findPoolTicketsFiltered(any(), any(), any());
    }

    @Test
    void getTeamTicketsPaged_withValue_passesPriority() {
        when(userRepository.findById("u-1")).thenReturn(Optional.of(
                User.builder().id("u-1").authorizedProducts(List.of(product)).build()));
        when(ticketRepository.findTeamTicketsFiltered(any(), any(), eq(List.of("HIGH")), any()))
                .thenReturn(new PageImpl<>(List.of()));
        ticketService.getTeamTicketsPaged("u-1", List.of("AGENT"), "HIGH", PageRequest.of(0, 10));
        org.mockito.Mockito.verify(ticketRepository).findTeamTicketsFiltered(any(), any(), eq(List.of("HIGH")), any());
    }

    @Test
    void getAgentClaimedTicketsPaged_withValues_passesParams() {
        when(ticketClaimRepository.findTicketIdsByAgentId("a-1")).thenReturn(List.of(1L));
        when(ticketRepository.findClaimedTicketsFiltered(eq(List.of(1L)), eq(List.of("NEW")), eq(List.of("HIGH")), any()))
                .thenReturn(new PageImpl<>(List.of()));
        ticketService.getAgentClaimedTicketsPaged("a-1", "NEW", "HIGH", PageRequest.of(0, 10));
        org.mockito.Mockito.verify(ticketRepository).findClaimedTicketsFiltered(eq(List.of(1L)), eq(List.of("NEW")), eq(List.of("HIGH")), any());
    }

    @Test
    void getTicketsByProductFiltered_customerStandardPath() {
        when(ticketRepository.findByProductIdAndCustomerIdFiltered(eq(10L), eq("c-1"), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));

        Page<Ticket> result = ticketService.getTicketsByProductFiltered(10L, "c-1", List.of("CUSTOMER"),
                TicketFilterDTO.builder().build(), PageRequest.of(0, 10));

        assertNotNull(result);
    }

    @Test
    void assignTicket_targetAlreadyClaimed_returnsWithoutChange() {
        Ticket existing = Ticket.builder().id(2500L).status("IN_PROGRESS")
                .productId(10L).customerId("c-1").build();
        when(ticketRepository.findById(2500L)).thenReturn(Optional.of(existing));
        when(userRepository.findById("admin-1")).thenReturn(Optional.of(agentAdmin));
        when(userRepository.findById("agent-1")).thenReturn(Optional.of(agent));
        when(productRepository.findById(10L)).thenReturn(Optional.of(product));
        when(ticketClaimRepository.existsByTicketIdAndAgentId(2500L, "agent-1")).thenReturn(true);

        Ticket result = ticketService.assignTicket(2500L, "agent-1", "admin-1", "note");

        assertEquals("IN_PROGRESS", result.getStatus());
        verify(ticketClaimRepository, never()).save(any());
    }

    // =====================================================================
    // Filtreli liste dispatcher'ları — sort tipi (priority/sla/csat × asc/desc),
    // extra-filtre ve default dallarının her birini doğru repository sorgusuna
    // yönlendirdiğini doğrular; helper'ları (statusesOrAll, csatRatingsOrPlaceholder,
    // hasExtraFilters, …) çeşitli filtre DTO'larıyla çalıştırır.
    // =====================================================================

    private static final List<String> MGR_ROLES = List.of("MANAGER");

    private Pageable sortedBy(Sort.Direction dir, String prop) {
        return PageRequest.of(0, 20, Sort.by(dir, prop));
    }

    /** Tüm yardımcıların "filtre aktif" dallarını tetikleyen zengin filtre. */
    private TicketFilterDTO richFilter() {
        return TicketFilterDTO.builder()
                .search("vpn")
                .statuses(List.of("NEW", "IN_PROGRESS"))
                .priorities(List.of("HIGH", "LOW"))
                .productIds(List.of(10L))
                .slaStatuses(List.of("BREACHED"))
                .agentIds(List.of("agent-1"))
                .topicIds(List.of(50L))
                .csatRatings(List.of("5", "NONE", "notnum"))
                .createdAtFrom(ZonedDateTime.now().minusDays(7))
                .createdAtTo(ZonedDateTime.now())
                .build();
    }

    private TicketFilterDTO emptyFilter() {
        return TicketFilterDTO.builder().build();
    }

    // ---- getAllAccessibleTicketsFiltered (global scope = MANAGER) ----

    @Test
    void getAllAccessibleTicketsFiltered_nullUser_returnsEmpty() {
        Page<Ticket> page = ticketService.getAllAccessibleTicketsFiltered(null, MGR_ROLES, emptyFilter(), sortedBy(Sort.Direction.ASC, "id"));
        assertEquals(0, page.getTotalElements());
    }

    @Test
    void getAllAccessibleTicketsFiltered_noProducts_returnsEmpty() {
        when(productRepository.findAll()).thenReturn(List.of());
        Page<Ticket> page = ticketService.getAllAccessibleTicketsFiltered("mgr-1", MGR_ROLES, emptyFilter(), sortedBy(Sort.Direction.ASC, "id"));
        assertEquals(0, page.getTotalElements());
    }

    @Test
    void getAllAccessibleTicketsFiltered_sortByPriority_routesToPriorityQuery() {
        when(productRepository.findAll()).thenReturn(List.of(product));
        ticketService.getAllAccessibleTicketsFiltered("mgr-1", MGR_ROLES, emptyFilter(), sortedBy(Sort.Direction.ASC, "priority"));
        verify(ticketRepository).findTeamTicketsFilteredOrderByPriorityAsc(any(), any(), any(), any());
        ticketService.getAllAccessibleTicketsFiltered("mgr-1", MGR_ROLES, emptyFilter(), sortedBy(Sort.Direction.DESC, "priority"));
        verify(ticketRepository).findTeamTicketsFilteredOrderByPriorityDesc(any(), any(), any(), any());
    }

    @Test
    void getAllAccessibleTicketsFiltered_sortBySla_routesToSlaQuery() {
        when(productRepository.findAll()).thenReturn(List.of(product));
        ticketService.getAllAccessibleTicketsFiltered("mgr-1", MGR_ROLES, emptyFilter(), sortedBy(Sort.Direction.ASC, "slaDeadline"));
        verify(ticketRepository).findTeamTicketsFilteredOrderBySlaUrgencyAsc(any(), any(), any(), any());
        ticketService.getAllAccessibleTicketsFiltered("mgr-1", MGR_ROLES, emptyFilter(), sortedBy(Sort.Direction.DESC, "slaDeadline"));
        verify(ticketRepository).findTeamTicketsFilteredOrderBySlaUrgencyDesc(any(), any(), any(), any());
    }

    @Test
    void getAllAccessibleTicketsFiltered_sortByCsat_runsFullFilteredCsatBranch() {
        when(productRepository.findAll()).thenReturn(List.of(product));
        assertDoesNotThrow(() -> ticketService.getAllAccessibleTicketsFiltered("mgr-1", MGR_ROLES, richFilter(), sortedBy(Sort.Direction.ASC, "csatRating")));
        assertDoesNotThrow(() -> ticketService.getAllAccessibleTicketsFiltered("mgr-1", MGR_ROLES, richFilter(), sortedBy(Sort.Direction.DESC, "csatRating")));
    }

    @Test
    void getAllAccessibleTicketsFiltered_extraFilters_runsFullFilteredBranch() {
        when(productRepository.findAll()).thenReturn(List.of(product));
        assertDoesNotThrow(() -> ticketService.getAllAccessibleTicketsFiltered("mgr-1", MGR_ROLES, richFilter(), sortedBy(Sort.Direction.ASC, "createdAt")));
    }

    @Test
    void getAllAccessibleTicketsFiltered_noSortNoExtra_routesToSimpleQuery() {
        when(productRepository.findAll()).thenReturn(List.of(product));
        ticketService.getAllAccessibleTicketsFiltered("mgr-1", MGR_ROLES, emptyFilter(), PageRequest.of(0, 20));
        verify(ticketRepository).findTeamTicketsFiltered(any(), any(), any(), any());
    }

    // ---- getTeamTicketsFiltered ----

    @Test
    void getTeamTicketsFiltered_nullUser_returnsEmpty() {
        Page<Ticket> page = ticketService.getTeamTicketsFiltered(null, MGR_ROLES, emptyFilter(), PageRequest.of(0, 20));
        assertEquals(0, page.getTotalElements());
    }

    @Test
    void getTeamTicketsFiltered_sortBranches_routeCorrectly() {
        when(productRepository.findAll()).thenReturn(List.of(product));
        ticketService.getTeamTicketsFiltered("mgr-1", MGR_ROLES, emptyFilter(), sortedBy(Sort.Direction.ASC, "priority"));
        verify(ticketRepository).findTeamTicketsFilteredOrderByPriorityAsc(any(), any(), any(), any());
        ticketService.getTeamTicketsFiltered("mgr-1", MGR_ROLES, emptyFilter(), sortedBy(Sort.Direction.DESC, "slaDeadline"));
        verify(ticketRepository).findTeamTicketsFilteredOrderBySlaUrgencyDesc(any(), any(), any(), any());
        ticketService.getTeamTicketsFiltered("mgr-1", MGR_ROLES, emptyFilter(), PageRequest.of(0, 20));
        verify(ticketRepository).findTeamTicketsFiltered(any(), any(), any(), any());
    }

    @Test
    void getTeamTicketsFiltered_csatAndExtra_runFullFiltered() {
        when(productRepository.findAll()).thenReturn(List.of(product));
        assertDoesNotThrow(() -> ticketService.getTeamTicketsFiltered("mgr-1", MGR_ROLES, richFilter(), sortedBy(Sort.Direction.ASC, "csatRating")));
        assertDoesNotThrow(() -> ticketService.getTeamTicketsFiltered("mgr-1", MGR_ROLES, richFilter(), sortedBy(Sort.Direction.ASC, "createdAt")));
    }

    // ---- getAgentClaimedTicketsFiltered ----

    @Test
    void getAgentClaimedTicketsFiltered_noClaims_returnsEmpty() {
        when(ticketClaimRepository.findTicketIdsByAgentId("agent-1")).thenReturn(List.of());
        Page<Ticket> page = ticketService.getAgentClaimedTicketsFiltered("agent-1", emptyFilter(), PageRequest.of(0, 20));
        assertEquals(0, page.getTotalElements());
    }

    @Test
    void getAgentClaimedTicketsFiltered_sortBranches_routeCorrectly() {
        when(ticketClaimRepository.findTicketIdsByAgentId("agent-1")).thenReturn(List.of(1L, 2L));
        ticketService.getAgentClaimedTicketsFiltered("agent-1", emptyFilter(), sortedBy(Sort.Direction.ASC, "priority"));
        verify(ticketRepository).findClaimedTicketsFilteredOrderByPriorityAsc(any(), any(), any(), any());
        ticketService.getAgentClaimedTicketsFiltered("agent-1", emptyFilter(), sortedBy(Sort.Direction.DESC, "slaDeadline"));
        verify(ticketRepository).findClaimedTicketsFilteredOrderBySlaUrgencyDesc(any(), any(), any(), any());
        ticketService.getAgentClaimedTicketsFiltered("agent-1", emptyFilter(), PageRequest.of(0, 20));
        verify(ticketRepository).findClaimedTicketsFiltered(any(), any(), any(), any());
    }

    @Test
    void getAgentClaimedTicketsFiltered_csatAndExtra_runFullFiltered() {
        when(ticketClaimRepository.findTicketIdsByAgentId("agent-1")).thenReturn(List.of(1L, 2L));
        assertDoesNotThrow(() -> ticketService.getAgentClaimedTicketsFiltered("agent-1", richFilter(), sortedBy(Sort.Direction.DESC, "csatRating")));
        assertDoesNotThrow(() -> ticketService.getAgentClaimedTicketsFiltered("agent-1", richFilter(), sortedBy(Sort.Direction.ASC, "createdAt")));
    }

    // ---- helper "filtre yok" (absent) dalları + csat varyantları ----

    @Test
    void csatSort_emptyFilter_coversAbsentHelperBranches() {
        when(productRepository.findAll()).thenReturn(List.of(product));
        // emptyFilter → statusesOrAll/prioritiesOrAll/productIdsOrAll/slaStatusesOrAll absent dalları,
        // csatFilterActive=false, csatRatingsOrPlaceholder(null→[-1]), csatIncludeNone=false, toSearchPattern(null→null)
        assertDoesNotThrow(() -> ticketService.getAllAccessibleTicketsFiltered("mgr-1", MGR_ROLES, emptyFilter(), sortedBy(Sort.Direction.ASC, "csatRating")));
    }

    @Test
    void csatSort_noneOnlyRatings_coversCsatPlaceholderAndIncludeNone() {
        when(productRepository.findAll()).thenReturn(List.of(product));
        // csatRatings=["NONE"] → sayısal yok → placeholder [-1]; csatIncludeNone=true; csatFilterActive=true
        TicketFilterDTO noneOnly = TicketFilterDTO.builder().csatRatings(List.of("NONE")).build();
        assertDoesNotThrow(() -> ticketService.getAllAccessibleTicketsFiltered("mgr-1", MGR_ROLES, noneOnly, sortedBy(Sort.Direction.ASC, "csatRating")));
    }

    @Test
    void hasExtraFilters_eachSingleTrigger_reachesFullFiltered() {
        when(productRepository.findAll()).thenReturn(List.of(product));
        List<TicketFilterDTO> singles = List.of(
                TicketFilterDTO.builder().search("x").build(),
                TicketFilterDTO.builder().createdAtFrom(ZonedDateTime.now().minusDays(1)).build(),
                TicketFilterDTO.builder().createdAtTo(ZonedDateTime.now()).build(),
                TicketFilterDTO.builder().slaStatuses(List.of("BREACHED")).build(),
                TicketFilterDTO.builder().agentIds(List.of("a1")).build(),
                TicketFilterDTO.builder().topicIds(List.of(5L)).build(),
                TicketFilterDTO.builder().productIds(List.of(10L)).build(),
                TicketFilterDTO.builder().statuses(List.of("NEW", "CLOSED")).build(),
                TicketFilterDTO.builder().priorities(List.of("HIGH", "LOW")).build()
        );
        for (TicketFilterDTO f : singles) {
            assertDoesNotThrow(() -> ticketService.getAllAccessibleTicketsFiltered("mgr-1", MGR_ROLES, f, PageRequest.of(0, 20)));
        }
    }

    // ---- getTicketsByProductPaged / getTicketsByProductFiltered ----

    @Test
    void getTicketsByProductPaged_statusAndPriorityPresentAndAbsent() {
        ticketService.getTicketsByProductPaged(10L, "mgr-1", MGR_ROLES, "NEW", "HIGH", PageRequest.of(0, 20));
        verify(ticketRepository).findByProductIdFiltered(eq(10L), any(), any(), any());
        ticketService.getTicketsByProductPaged(10L, "mgr-1", MGR_ROLES, null, null, PageRequest.of(0, 20));
        ticketService.getTicketsByProductPaged(10L, "mgr-1", MGR_ROLES, "  ", "  ", PageRequest.of(0, 20));
    }

    @Test
    void getTicketsByProductFiltered_globalRole_sortAndExtraBranches() {
        ticketService.getTicketsByProductFiltered(10L, "mgr-1", MGR_ROLES, emptyFilter(), sortedBy(Sort.Direction.ASC, "priority"));
        verify(ticketRepository).findByProductIdFilteredOrderByPriorityAsc(eq(10L), any(), any(), any());
        ticketService.getTicketsByProductFiltered(10L, "mgr-1", MGR_ROLES, emptyFilter(), sortedBy(Sort.Direction.DESC, "slaDeadline"));
        verify(ticketRepository).findByProductIdFilteredOrderBySlaUrgencyDesc(eq(10L), any(), any(), any());
        assertDoesNotThrow(() -> ticketService.getTicketsByProductFiltered(10L, "mgr-1", MGR_ROLES, TicketFilterDTO.builder().search("x").build(), PageRequest.of(0, 20)));
        ticketService.getTicketsByProductFiltered(10L, "mgr-1", MGR_ROLES, emptyFilter(), PageRequest.of(0, 20));
        verify(ticketRepository).findByProductIdFiltered(eq(10L), any(), any(), any());
    }

    @Test
    void getTicketsByProductFiltered_agentAuthorizedAndUnauthorized() {
        when(userRepository.findById("agent-1")).thenReturn(java.util.Optional.of(agent)); // agent authorized for product 10
        ticketService.getTicketsByProductFiltered(10L, "agent-1", List.of("AGENT"), emptyFilter(), PageRequest.of(0, 20));
        verify(ticketRepository).findByProductIdFiltered(eq(10L), any(), any(), any());

        // 999 ürünü için yetkisiz → boş sayfa
        Page<Ticket> page = ticketService.getTicketsByProductFiltered(999L, "agent-1", List.of("AGENT"), emptyFilter(), PageRequest.of(0, 20));
        assertEquals(0, page.getTotalElements());
    }

    @Test
    void getTicketsByProductFiltered_customer_sortAndDefaultBranches() {
        List<String> cust = List.of("CUSTOMER");
        ticketService.getTicketsByProductFiltered(10L, "customer-1", cust, emptyFilter(), sortedBy(Sort.Direction.ASC, "priority"));
        verify(ticketRepository).findByProductIdAndCustomerIdFilteredOrderByPriorityAsc(eq(10L), eq("customer-1"), any(), any(), any());
        ticketService.getTicketsByProductFiltered(10L, "customer-1", cust, emptyFilter(), sortedBy(Sort.Direction.ASC, "slaDeadline"));
        verify(ticketRepository).findByProductIdAndCustomerIdFilteredOrderBySlaUrgencyAsc(eq(10L), eq("customer-1"), any(), any(), any());
        assertDoesNotThrow(() -> ticketService.getTicketsByProductFiltered(10L, "customer-1", cust, TicketFilterDTO.builder().search("x").build(), PageRequest.of(0, 20)));
        ticketService.getTicketsByProductFiltered(10L, "customer-1", cust, emptyFilter(), PageRequest.of(0, 20));
        verify(ticketRepository).findByProductIdAndCustomerIdFiltered(eq(10L), eq("customer-1"), any(), any(), any());
    }

    @Test
    void getTicketsByProductFiltered_noMatchingRole_returnsEmpty() {
        Page<Ticket> page = ticketService.getTicketsByProductFiltered(10L, "u-1", List.of("UNKNOWN"), emptyFilter(), PageRequest.of(0, 20));
        assertEquals(0, page.getTotalElements());
    }

    // ---- updateTicketTopic dalları (ADMIN auth) ----

    private static final List<String> ADMIN_LIST = List.of("ADMIN");

    private Ticket ticketWithTopic(Long topicId) {
        return Ticket.builder().id(700L).productId(10L).topicId(topicId)
                .status("IN_PROGRESS").customerId("customer-1").priority("HIGH").build();
    }

    @Test
    void updateTicketTopic_nullTopic_throwsBadRequest() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> ticketService.updateTicketTopic(700L, null, null, null, "admin-1", ADMIN_LIST));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    void updateTicketTopic_sameTopic_returnsWithoutChange() {
        when(ticketRepository.findById(700L)).thenReturn(Optional.of(ticketWithTopic(50L)));
        Ticket result = ticketService.updateTicketTopic(700L, 50L, null, null, "admin-1", ADMIN_LIST);
        assertEquals(50L, result.getTopicId());
        verify(ticketRepository, never()).save(any());
    }

    @Test
    void updateTicketTopic_topicNotFound_throwsNotFound() {
        when(ticketRepository.findById(700L)).thenReturn(Optional.of(ticketWithTopic(50L)));
        when(ticketTopicRepository.findById(60L)).thenReturn(Optional.empty());
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> ticketService.updateTicketTopic(700L, 60L, null, null, "admin-1", ADMIN_LIST));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    @Test
    void updateTicketTopic_productMismatch_throwsBadRequest() {
        when(ticketRepository.findById(700L)).thenReturn(Optional.of(ticketWithTopic(50L)));
        TicketTopic otherProductTopic = TicketTopic.builder().id(60L).productId(99L).name("X").isActive(true).build();
        when(ticketTopicRepository.findById(60L)).thenReturn(Optional.of(otherProductTopic));
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> ticketService.updateTicketTopic(700L, 60L, null, null, "admin-1", ADMIN_LIST));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    void updateTicketTopic_inactiveTopic_throwsBadRequest() {
        when(ticketRepository.findById(700L)).thenReturn(Optional.of(ticketWithTopic(50L)));
        TicketTopic inactive = TicketTopic.builder().id(60L).productId(10L).name("X").isActive(false).build();
        when(ticketTopicRepository.findById(60L)).thenReturn(Optional.of(inactive));
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> ticketService.updateTicketTopic(700L, 60L, null, null, "admin-1", ADMIN_LIST));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    void updateTicketTopic_validChange_savesAndSnapshotsName() {
        when(ticketRepository.findById(700L)).thenReturn(Optional.of(ticketWithTopic(50L)));
        TicketTopic newTopic = TicketTopic.builder().id(60L).productId(10L).name("Yeni Konu").isActive(true).build();
        when(ticketTopicRepository.findById(60L)).thenReturn(Optional.of(newTopic));
        when(ticketTopicRepository.findById(50L)).thenReturn(Optional.of(topic));
        when(ticketRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Ticket result = ticketService.updateTicketTopic(700L, 60L, null, null, "admin-1", ADMIN_LIST);

        assertEquals(60L, result.getTopicId());
        assertEquals("Yeni Konu", result.getTopicNameSnapshot());
    }

    // ---- validateMutationAccess: lead + customer dalları ----

    @Test
    void validateMutationAccess_leadAuthorized_returnsTicket() {
        User lead = User.builder().id("lead-1").role("LEAD_AGENT").authorizedProducts(List.of(product)).build();
        when(ticketRepository.findById(700L)).thenReturn(Optional.of(ticketWithTopic(50L)));
        when(userRepository.findById("lead-1")).thenReturn(Optional.of(lead));
        assertNotNull(ticketService.validateMutationAccess(700L, "lead-1", List.of("LEAD_AGENT")));
    }

    @Test
    void validateMutationAccess_leadUnauthorizedProduct_forbidden() {
        User lead = User.builder().id("lead-2").role("LEAD_AGENT")
                .authorizedProducts(List.of(Product.builder().id(99L).build())).build();
        when(ticketRepository.findById(700L)).thenReturn(Optional.of(ticketWithTopic(50L)));
        when(userRepository.findById("lead-2")).thenReturn(Optional.of(lead));
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> ticketService.validateMutationAccess(700L, "lead-2", List.of("LEAD_AGENT")));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
    }

    @Test
    void validateMutationAccess_customerOwnTicket_returnsTicket() {
        when(ticketRepository.findById(700L)).thenReturn(Optional.of(ticketWithTopic(50L)));
        assertNotNull(ticketService.validateMutationAccess(700L, "customer-1", List.of("CUSTOMER")));
    }

    @Test
    void validateMutationAccess_customerOtherTicket_forbidden() {
        when(ticketRepository.findById(700L)).thenReturn(Optional.of(ticketWithTopic(50L)));
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> ticketService.validateMutationAccess(700L, "other-customer", List.of("CUSTOMER")));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
    }

    // ---- updateTicketStatus: validateStatusChangePermission lead/customer dalları ----

    private Ticket statusTicket(String status) {
        // processInstanceId null → BPMN doğrulaması atlanır, izin mantığına odaklanılır.
        return Ticket.builder().id(800L).productId(10L).status(status)
                .customerId("customer-1").priority("HIGH").processInstanceId(null).build();
    }

    @Test
    void updateTicketStatus_leadAuthorized_succeeds() {
        when(ticketRepository.findById(800L)).thenReturn(Optional.of(statusTicket("IN_PROGRESS")));
        when(ticketRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        User lead = User.builder().id("lead-1").role("LEAD_AGENT").authorizedProducts(List.of(product)).build();
        when(userRepository.findById("lead-1")).thenReturn(Optional.of(lead));

        Ticket result = ticketService.updateTicketStatus(800L, "WAITING_FOR_CUSTOMER", null, null, "lead-1", List.of("LEAD_AGENT"));
        assertEquals("WAITING_FOR_CUSTOMER", result.getStatus());
    }

    @Test
    void updateTicketStatus_leadUnauthorizedProduct_forbidden() {
        when(ticketRepository.findById(800L)).thenReturn(Optional.of(statusTicket("IN_PROGRESS")));
        User lead = User.builder().id("lead-2").role("LEAD_AGENT")
                .authorizedProducts(List.of(Product.builder().id(99L).build())).build();
        when(userRepository.findById("lead-2")).thenReturn(Optional.of(lead));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> ticketService.updateTicketStatus(800L, "WAITING_FOR_CUSTOMER", null, null, "lead-2", List.of("LEAD_AGENT")));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
    }

    @Test
    void updateTicketStatus_customerAllowedTransition_succeeds() {
        when(ticketRepository.findById(800L)).thenReturn(Optional.of(statusTicket("WAITING_FOR_CUSTOMER")));
        when(ticketRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Ticket result = ticketService.updateTicketStatus(800L, "IN_PROGRESS", null, null, "customer-1", List.of("CUSTOMER"));
        assertEquals("IN_PROGRESS", result.getStatus());
    }

    @Test
    void updateTicketStatus_customerDisallowedTransition_forbidden() {
        when(ticketRepository.findById(800L)).thenReturn(Optional.of(statusTicket("IN_PROGRESS")));
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> ticketService.updateTicketStatus(800L, "WAITING_FOR_CUSTOMER", null, null, "customer-1", List.of("CUSTOMER")));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
    }

    @Test
    void updateTicketStatus_customerOtherTicket_forbidden() {
        when(ticketRepository.findById(800L)).thenReturn(Optional.of(statusTicket("WAITING_FOR_CUSTOMER")));
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> ticketService.updateTicketStatus(800L, "IN_PROGRESS", null, null, "other-cust", List.of("CUSTOMER")));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
    }

    // ---- updateTicketPriority dalları ----

    @Test
    void updateTicketPriority_invalidPriority_throwsBadRequest() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> ticketService.updateTicketPriority(800L, "URGENT", null, null, "admin-1", ADMIN_LIST));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    void updateTicketPriority_samePriority_returnsUnchanged() {
        when(ticketRepository.findById(800L)).thenReturn(Optional.of(statusTicket("IN_PROGRESS"))); // priority HIGH
        Ticket result = ticketService.updateTicketPriority(800L, "HIGH", null, null, "admin-1", ADMIN_LIST);
        assertEquals("HIGH", result.getPriority());
        verify(ticketRepository, never()).save(any());
    }

    @Test
    void updateTicketPriority_validChangeSlaActive_pausesAndSaves() {
        Ticket t = Ticket.builder().id(800L).productId(10L).status("IN_PROGRESS")
                .customerId("customer-1").priority("HIGH").slaBreached(false).build();
        when(ticketRepository.findById(800L)).thenReturn(Optional.of(t));
        when(ticketRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Ticket result = ticketService.updateTicketPriority(800L, "CRITICAL", null, null, "admin-1", ADMIN_LIST);

        assertEquals("CRITICAL", result.getPriority());
        verify(workflowService).pauseSla(t);
    }
}

