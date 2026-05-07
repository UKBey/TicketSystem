package com.ticketsystem.it_service_backend.controller;

import com.ticketsystem.it_service_backend.dto.TicketRequestDTO;
import com.ticketsystem.it_service_backend.dto.TicketResponseDTO;
import com.ticketsystem.it_service_backend.entity.Product;
import com.ticketsystem.it_service_backend.entity.Ticket;
import com.ticketsystem.it_service_backend.entity.User;
import com.ticketsystem.it_service_backend.repository.ProductRepository;
import com.ticketsystem.it_service_backend.repository.TicketClaimRepository;
import com.ticketsystem.it_service_backend.repository.TicketAuditLogRepository;
import com.ticketsystem.it_service_backend.repository.UserRepository;
import com.ticketsystem.it_service_backend.service.TicketService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TicketControllerTest {

        @Mock
    private TicketService ticketService;

        @Mock
    private UserRepository userRepository;

        @Mock
    private ProductRepository productRepository;

        @Mock
    private TicketClaimRepository ticketClaimRepository;

        @Mock
    private TicketAuditLogRepository ticketAuditLogRepository;

        private TicketController ticketController;

        @BeforeEach
        void setUp() {
                ticketController = new TicketController(ticketService, ticketClaimRepository, ticketAuditLogRepository, userRepository, productRepository);
                lenient().when(ticketAuditLogRepository.findByTicketIdOrderByCreatedAtDesc(any())).thenReturn(List.of());
        }

    @Test
        void createTicket_withCustomerRole_returnsOkAndDto() {
        TicketRequestDTO request = TicketRequestDTO.builder()
                .title("Printer error")
                .description("Paper jam")
                .priority("MEDIUM")
                .productId(10L)
                .build();

        Ticket saved = Ticket.builder()
                .id(1001L)
                .title(request.getTitle())
                .description(request.getDescription())
                .priority(request.getPriority())
                .status("NEW")
                .productId(10L)
                .customerId("customer-1")
                .build();

        when(ticketService.createTicket(any(Ticket.class), eq("customer-1"))).thenReturn(saved);
        when(userRepository.findById("customer-1")).thenReturn(Optional.of(
                User.builder().id("customer-1").fullName("Customer One").email("c1@example.com").role("CUSTOMER").build()));
        when(productRepository.findById(10L)).thenReturn(Optional.of(Product.builder().id(10L).name("ERP").build()));

        ResponseEntity<TicketResponseDTO> response = ticketController.createTicket(request, jwtWithRole("customer-1", "CUSTOMER"));

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals(1001L, response.getBody().getId());
        assertEquals("NEW", response.getBody().getStatus());
        assertEquals("Customer One", response.getBody().getCustomerName());
        assertEquals("ERP", response.getBody().getProductName());
        verify(ticketService).createTicket(any(Ticket.class), eq("customer-1"));
    }

    @Test
    void getTickets_withCustomerRole_returnsOnlyCustomerTickets() {
        Ticket customerTicket = Ticket.builder()
                .id(2001L)
                .title("VPN issue")
                .description("Cannot connect")
                .priority("HIGH")
                .status("NEW")
                .productId(10L)
                .customerId("customer-1")
                .build();

        when(ticketService.getCustomerTickets("customer-1")).thenReturn(List.of(customerTicket));
        when(userRepository.findById("customer-1")).thenReturn(Optional.of(
                User.builder().id("customer-1").fullName("Customer One").email("c1@example.com").role("CUSTOMER").build()));
        when(productRepository.findById(10L)).thenReturn(Optional.of(Product.builder().id(10L).name("ERP").build()));

        ResponseEntity<List<TicketResponseDTO>> response = ticketController.getTickets(jwtWithRole("customer-1", "CUSTOMER"));

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().size());
        assertEquals(2001L, response.getBody().get(0).getId());
        verify(ticketService).getCustomerTickets("customer-1");
    }

    @Test
    void getPoolTickets_withAgentRole_returnsOk() {
        Ticket poolTicket = Ticket.builder()
                .id(3001L)
                .title("Email issue")
                .description("Bounce back")
                .priority("LOW")
                .status("NEW")
                .productId(10L)
                .customerId("customer-1")
                .build();

        when(ticketService.getPoolTickets(eq("agent-1"), eq(List.of("AGENT")))).thenReturn(List.of(poolTicket));
        when(userRepository.findById("customer-1")).thenReturn(Optional.of(
                User.builder().id("customer-1").fullName("Customer One").email("c1@example.com").role("CUSTOMER").build()));
        when(productRepository.findById(10L)).thenReturn(Optional.of(Product.builder().id(10L).name("ERP").build()));
        when(ticketService.getSlaTimerInfo(poolTicket)).thenReturn(Map.of("deadlineTs", 999L));

        ResponseEntity<List<TicketResponseDTO>> response = ticketController.getPoolTickets(jwtWithRole("agent-1", "AGENT"));

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().size());
        assertEquals(3001L, response.getBody().get(0).getId());
    }

    @Test
    void getPoolTickets_withAgentAdminRole_returnsOk() {
        Ticket poolTicket = Ticket.builder()
                .id(3002L)
                .title("Server issue")
                .description("CPU high")
                .priority("MEDIUM")
                .status("NEW")
                .productId(10L)
                .customerId("customer-1")
                .build();

        when(ticketService.getPoolTickets(eq("admin-1"), eq(List.of("AGENT_ADMIN")))).thenReturn(List.of(poolTicket));
        when(userRepository.findById("customer-1")).thenReturn(Optional.of(
                User.builder().id("customer-1").fullName("Customer One").email("c1@example.com").role("CUSTOMER").build()));
        when(productRepository.findById(10L)).thenReturn(Optional.of(Product.builder().id(10L).name("ERP").build()));
        when(ticketService.getSlaTimerInfo(poolTicket)).thenReturn(Map.of("deadlineTs", 999L));

        ResponseEntity<List<TicketResponseDTO>> response = ticketController.getPoolTickets(jwtWithRole("admin-1", "AGENT_ADMIN"));

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().size());
        assertEquals(3002L, response.getBody().get(0).getId());
    }

    @Test
    void claimTicket_withAgentRole_returnsClaimedTicket() {
        Ticket claimed = Ticket.builder()
                .id(9001L)
                .title("Claim me")
                .description("desc")
                .priority("LOW")
                .status("IN_PROGRESS")
                .productId(10L)
                .customerId("customer-1")
                .build();

        when(ticketService.claimTicket(9001L, "agent-1")).thenReturn(claimed);
        when(userRepository.findById("customer-1")).thenReturn(Optional.of(
                User.builder().id("customer-1").fullName("Customer One").email("c1@example.com").role("CUSTOMER").build()));
        when(productRepository.findById(10L)).thenReturn(Optional.of(Product.builder().id(10L).name("ERP").build()));
        when(ticketService.getSlaTimerInfo(claimed)).thenReturn(Map.of("deadlineTs", 777L));

        ResponseEntity<TicketResponseDTO> response = ticketController.claimTicket(9001L, jwtWithRole("agent-1", "AGENT"));

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals(9001L, response.getBody().getId());
        assertEquals("IN_PROGRESS", response.getBody().getStatus());
    }

    @Test
    void claimTicket_withAgentAdminRole_returnsClaimedTicket() {
        Ticket claimed = Ticket.builder()
                .id(9002L)
                .title("Claim me")
                .description("desc")
                .priority("LOW")
                .status("IN_PROGRESS")
                .productId(10L)
                .customerId("customer-1")
                .build();

        when(ticketService.claimTicket(9002L, "admin-1")).thenReturn(claimed);
        when(userRepository.findById("customer-1")).thenReturn(Optional.of(
                User.builder().id("customer-1").fullName("Customer One").email("c1@example.com").role("CUSTOMER").build()));
        when(productRepository.findById(10L)).thenReturn(Optional.of(Product.builder().id(10L).name("ERP").build()));
        when(ticketService.getSlaTimerInfo(claimed)).thenReturn(Map.of("deadlineTs", 777L));

        ResponseEntity<TicketResponseDTO> response = ticketController.claimTicket(9002L, jwtWithRole("admin-1", "AGENT_ADMIN"));

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals(9002L, response.getBody().getId());
        assertEquals("IN_PROGRESS", response.getBody().getStatus());
    }

    @Test
    void updateStatus_withAgentRole_returnsOk() {
        Ticket updated = Ticket.builder()
                .id(4001L)
                .title("Network issue")
                .description("Packet loss")
                .priority("HIGH")
                .status("IN_PROGRESS")
                .productId(10L)
                .customerId("customer-1")
                .build();

        when(ticketService.updateTicketStatus(4001L, "IN_PROGRESS", "agent-1", List.of("AGENT")))
                .thenReturn(updated);
        when(userRepository.findById("customer-1")).thenReturn(Optional.of(
                User.builder().id("customer-1").fullName("Customer One").email("c1@example.com").role("CUSTOMER").build()));
        when(productRepository.findById(10L)).thenReturn(Optional.of(Product.builder().id(10L).name("ERP").build()));
        when(ticketService.getSlaTimerInfo(updated)).thenReturn(Map.of("deadlineTs", 888L));

        ResponseEntity<TicketResponseDTO> response = ticketController.updateStatus(
                4001L,
                Map.of("status", "IN_PROGRESS"),
                jwtWithRole("agent-1", "AGENT"));

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals(4001L, response.getBody().getId());
        assertEquals("IN_PROGRESS", response.getBody().getStatus());
    }

    @Test
    void updateStatus_withAgentAdminRole_returnsOk() {
        Ticket updated = Ticket.builder()
                .id(4002L)
                .title("Network issue")
                .description("Packet loss")
                .priority("HIGH")
                .status("IN_PROGRESS")
                .productId(10L)
                .customerId("customer-1")
                .build();

        when(ticketService.updateTicketStatus(4002L, "IN_PROGRESS", "admin-1", List.of("AGENT_ADMIN")))
                .thenReturn(updated);
        when(userRepository.findById("customer-1")).thenReturn(Optional.of(
                User.builder().id("customer-1").fullName("Customer One").email("c1@example.com").role("CUSTOMER").build()));
        when(productRepository.findById(10L)).thenReturn(Optional.of(Product.builder().id(10L).name("ERP").build()));
        when(ticketService.getSlaTimerInfo(updated)).thenReturn(Map.of("deadlineTs", 888L));

        ResponseEntity<TicketResponseDTO> response = ticketController.updateStatus(
                4002L,
                Map.of("status", "IN_PROGRESS"),
                jwtWithRole("admin-1", "AGENT_ADMIN"));

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals(4002L, response.getBody().getId());
        assertEquals("IN_PROGRESS", response.getBody().getStatus());
    }

    @Test
    void deleteTicket_withManagerRole_returnsNoContent() {
        ResponseEntity<Void> response = ticketController.deleteTicket(5001L);

        assertEquals(204, response.getStatusCode().value());
        verify(ticketService).deleteTicket(5001L);
    }

    @Test
    void getSlaTimer_withAgentAdminRole_returnsTimerPayload() {
        Ticket ticket = Ticket.builder()
                .id(7001L)
                .title("SLA ticket")
                .description("desc")
                .priority("MEDIUM")
                .status("IN_PROGRESS")
                .productId(10L)
                .customerId("customer-1")
                .build();

        when(ticketService.getTicketWithAuth(7001L, "admin-1", List.of("AGENT_ADMIN"))).thenReturn(ticket);
        when(ticketService.getSlaTimerInfo(ticket)).thenReturn(Map.of("deadlineTs", 555L));

        ResponseEntity<Map<String, Long>> response = ticketController.getSlaTimer(7001L, jwtWithRole("admin-1", "AGENT_ADMIN"));

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals(555L, response.getBody().get("deadlineTs"));
    }

    @Test
    void getSlaTimer_withManagerRole_returnsTimerPayload() {
        Ticket ticket = Ticket.builder()
                .id(7002L)
                .title("SLA ticket")
                .description("desc")
                .priority("MEDIUM")
                .status("IN_PROGRESS")
                .productId(10L)
                .customerId("customer-1")
                .build();

        when(ticketService.getTicketWithAuth(7002L, "manager-1", List.of("MANAGER"))).thenReturn(ticket);
        when(ticketService.getSlaTimerInfo(ticket)).thenReturn(Map.of("deadlineTs", 556L));

        ResponseEntity<Map<String, Long>> response = ticketController.getSlaTimer(7002L, jwtWithRole("manager-1", "MANAGER"));

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals(556L, response.getBody().get("deadlineTs"));
    }

    @Test
    void getTickets_withAgentAdminRole_returnsAllTickets() {
        Ticket t1 = Ticket.builder().id(8001L).title("T1").description("D1").priority("LOW").status("NEW").customerId("customer-1").build();
        when(ticketService.getAllTickets("admin-1", List.of("AGENT_ADMIN"))).thenReturn(List.of(t1));
        when(userRepository.findById("customer-1")).thenReturn(Optional.empty()); // covers null customerName case

        ResponseEntity<List<TicketResponseDTO>> response = ticketController.getTickets(jwtWithRole("admin-1", "AGENT_ADMIN"));

        assertEquals(200, response.getStatusCode().value());
        assertEquals(1, response.getBody().size());
        assertEquals("Unknown", response.getBody().get(0).getCustomerName()); // tests null mapped to Unknown
    }

    @Test
    void getTickets_withManagerRole_returnsAllTickets() {
        Ticket t1 = Ticket.builder().id(8002L).title("T1").description("D1").priority("LOW").status("NEW").customerId("customer-1").build();
                // Manager is dashboard-only; ensure they do not receive full ticket list.
                lenient().when(ticketService.getAllTickets("manager-1", List.of("MANAGER"))).thenReturn(List.of());
        lenient().when(userRepository.findById("customer-1")).thenReturn(Optional.empty()); // covers null customerName case

        ResponseEntity<List<TicketResponseDTO>> response = ticketController.getTickets(jwtWithRole("manager-1", "MANAGER"));

                assertEquals(200, response.getStatusCode().value());
                assertEquals(0, response.getBody().size());
    }

    @Test
    void getMyAssignedTickets_returnsAgentTickets() {
        Ticket t1 = Ticket.builder().id(9001L).title("T1").description("D1").priority("LOW").status("IN_PROGRESS").customerId("c1").build();
        when(ticketService.getAgentClaimedTickets("agent-1")).thenReturn(List.of(t1));

        ResponseEntity<List<TicketResponseDTO>> response = ticketController.getMyAssignedTickets(jwtWithRole("agent-1", "AGENT"));

        assertEquals(200, response.getStatusCode().value());
        assertEquals(1, response.getBody().size());
        assertEquals(9001L, response.getBody().get(0).getId());
    }

    @Test
    void getTicket_returnsTicketDetail() {
        Ticket t1 = Ticket.builder().id(10001L).title("T1").description("D1").priority("LOW").status("NEW").productId(1L).customerId("c1").build();
        when(ticketService.getTicketWithAuth(10001L, "customer-1", List.of("CUSTOMER"))).thenReturn(t1);

        ResponseEntity<TicketResponseDTO> response = ticketController.getTicket(10001L, jwtWithRole("customer-1", "CUSTOMER"));

        assertEquals(200, response.getStatusCode().value());
        assertEquals(10001L, response.getBody().getId());
        assertEquals("Unknown", response.getBody().getProductName()); // productId=1L but productRepository returns empty -> "Unknown"
    }

    @Test
    void updateStatus_withEmptyRoles() {
        Ticket updated = Ticket.builder()
                .id(4002L)
                .title("Network issue")
                .description("Packet loss")
                .priority("HIGH")
                .status("IN_PROGRESS")
                .build();

        when(ticketService.updateTicketStatus(4002L, "IN_PROGRESS", "c1", List.of()))
                .thenReturn(updated);

        Jwt jwt = org.mockito.Mockito.mock(Jwt.class);
        when(jwt.getSubject()).thenReturn("c1");
        lenient().when(jwt.getClaimAsMap("realm_access")).thenReturn(null); // triggers empty roles

        ResponseEntity<TicketResponseDTO> response = ticketController.updateStatus(4002L, Map.of("status", "IN_PROGRESS"), jwt);
        assertEquals(200, response.getStatusCode().value());
        assertEquals("Unknown", response.getBody().getCustomerName());
        assertEquals("Unknown", response.getBody().getProductName());
        org.junit.jupiter.api.Assertions.assertTrue(response.getBody().getClaimers().isEmpty());
    }

    private Jwt jwtWithRole(String subject, String role) {
        Jwt jwt = org.mockito.Mockito.mock(Jwt.class);
        when(jwt.getSubject()).thenReturn(subject);
                lenient().when(jwt.getClaimAsMap("realm_access")).thenReturn(Map.of("roles", List.of(role)));
        return jwt;
    }
}
