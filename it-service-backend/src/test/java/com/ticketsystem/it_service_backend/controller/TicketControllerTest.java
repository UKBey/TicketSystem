package com.ticketsystem.it_service_backend.controller;

import com.ticketsystem.it_service_backend.dto.AssignTicketRequestDTO;
import com.ticketsystem.it_service_backend.dto.TicketFilterDTO;
import com.ticketsystem.it_service_backend.dto.TicketRequestDTO;
import com.ticketsystem.it_service_backend.dto.TicketResponseDTO;
import com.ticketsystem.it_service_backend.dto.StatusUpdateRequestDTO;
import com.ticketsystem.it_service_backend.dto.UnclaimRequestDTO;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
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

    @Mock private TicketService ticketService;
    @Mock private UserRepository userRepository;
    @Mock private ProductRepository productRepository;
    @Mock private TicketClaimRepository ticketClaimRepository;
    @Mock private TicketAuditLogRepository ticketAuditLogRepository;

    private TicketController ticketController;

    @BeforeEach
    void setUp() {
        ticketController = new TicketController(ticketService, ticketClaimRepository,
                ticketAuditLogRepository, userRepository, productRepository);
        lenient().when(ticketAuditLogRepository.findByTicketIdOrderByCreatedAtDesc(any())).thenReturn(List.of());
    }

    // -----------------------------------------------------------------------
    // createTicket
    // -----------------------------------------------------------------------

    @Test
    void createTicket_withCustomerRole_returnsOkAndDto() {
        TicketRequestDTO request = TicketRequestDTO.builder()
                .title("Printer error").description("Paper jam").priority("MEDIUM").productId(10L).build();

        Ticket saved = Ticket.builder().id(1001L).title(request.getTitle())
                .description(request.getDescription()).priority(request.getPriority())
                .status("NEW").productId(10L).customerId("customer-1").build();

        when(ticketService.createTicket(any(Ticket.class), eq("customer-1"))).thenReturn(saved);
        when(userRepository.findById("customer-1")).thenReturn(Optional.of(
                User.builder().id("customer-1").fullName("Customer One").email("c1@example.com").role("CUSTOMER").build()));
        when(productRepository.findById(10L)).thenReturn(Optional.of(Product.builder().id(10L).name("ERP").build()));

        ResponseEntity<TicketResponseDTO> response = ticketController.createTicket(request, jwtWithRole("customer-1", "CUSTOMER"));

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals(1001L, response.getBody().getId());
        assertEquals("Customer One", response.getBody().getCustomerName());
        assertEquals("ERP", response.getBody().getProductName());
        verify(ticketService).createTicket(any(Ticket.class), eq("customer-1"));
    }

    // -----------------------------------------------------------------------
    // getTickets
    // -----------------------------------------------------------------------

    @Test
    void getTickets_withCustomerRole_returnsOnlyCustomerTickets() {
        Ticket t = Ticket.builder().id(2001L).title("VPN issue").description("Cannot connect")
                .priority("HIGH").status("NEW").productId(10L).customerId("customer-1").build();

        Page<Ticket> page = new PageImpl<>(List.of(t));
        when(ticketService.getCustomerTicketsFiltered(eq("customer-1"), any(TicketFilterDTO.class), any(Pageable.class)))
                .thenReturn(page);
        when(userRepository.findById("customer-1")).thenReturn(Optional.of(
                User.builder().id("customer-1").fullName("Customer One").email("c1@example.com").role("CUSTOMER").build()));
        when(productRepository.findById(10L)).thenReturn(Optional.of(Product.builder().id(10L).name("ERP").build()));

        ResponseEntity<Page<TicketResponseDTO>> response = ticketController.getTickets(
                jwtWithRole("customer-1", "CUSTOMER"),
                0, 20, "createdAt", "desc",
                null, null, null, null, null, null, null, null, null);

        assertEquals(200, response.getStatusCode().value());
        assertEquals(1, response.getBody().getContent().size());
        assertEquals(2001L, response.getBody().getContent().get(0).getId());
    }

    @Test
    void getTickets_withAdminRole_returnsAllTickets() {
        Ticket t1 = Ticket.builder().id(8001L).title("T1").description("D1")
                .priority("LOW").status("NEW").customerId("customer-1").build();
        Page<Ticket> page = new PageImpl<>(List.of(t1));
        // ADMIN is global: it goes through the team/all-products listing path.
        when(ticketService.getTeamTicketsFiltered(eq("admin-1"), eq(List.of("ADMIN")),
                any(TicketFilterDTO.class), any(Pageable.class))).thenReturn(page);
        when(userRepository.findById("customer-1")).thenReturn(Optional.empty());

        ResponseEntity<Page<TicketResponseDTO>> response = ticketController.getTickets(
                jwtWithRole("admin-1", "ADMIN"),
                0, 20, "createdAt", "desc",
                null, null, null, null, null, null, null, null, null);

        assertEquals(200, response.getStatusCode().value());
        assertEquals(1, response.getBody().getContent().size());
        assertEquals("Unknown", response.getBody().getContent().get(0).getCustomerName());
    }

    @Test
    void getTickets_withManagerRole_returnsEmptyList() {
        Page<Ticket> emptyPage = Page.empty();
        // MANAGER is now a global role and routes through the team/all-products listing path.
        when(ticketService.getTeamTicketsFiltered(eq("manager-1"), eq(List.of("MANAGER")),
                any(TicketFilterDTO.class), any(Pageable.class))).thenReturn(emptyPage);

        ResponseEntity<Page<TicketResponseDTO>> response = ticketController.getTickets(
                jwtWithRole("manager-1", "MANAGER"),
                0, 20, "createdAt", "desc",
                null, null, null, null, null, null, null, null, null);

        assertEquals(200, response.getStatusCode().value());
        assertEquals(0, response.getBody().getContent().size());
    }

    // -----------------------------------------------------------------------
    // getPoolTickets
    // -----------------------------------------------------------------------

    @Test
    void getPoolTickets_withAgentRole_returnsOk() {
        Ticket t = Ticket.builder().id(3001L).title("Email issue").description("Bounce back")
                .priority("LOW").status("NEW").productId(10L).customerId("customer-1").build();

        Page<Ticket> page = new PageImpl<>(List.of(t));
        when(ticketService.getPoolTicketsFiltered(eq("agent-1"), eq(List.of("AGENT")),
                any(TicketFilterDTO.class), any(Pageable.class))).thenReturn(page);
        when(userRepository.findById("customer-1")).thenReturn(Optional.of(
                User.builder().id("customer-1").fullName("Customer One").email("c1@example.com").role("CUSTOMER").build()));
        when(productRepository.findById(10L)).thenReturn(Optional.of(Product.builder().id(10L).name("ERP").build()));
        when(ticketService.getSlaTimerInfo(t)).thenReturn(Map.<String, Object>of("deadlineTs", 999L));

        ResponseEntity<Page<TicketResponseDTO>> response = ticketController.getPoolTickets(
                jwtWithRole("agent-1", "AGENT"),
                0, 20, "createdAt", "desc",
                null, null, null, null, null, null, null, null);

        assertEquals(200, response.getStatusCode().value());
        assertEquals(1, response.getBody().getContent().size());
        assertEquals(3001L, response.getBody().getContent().get(0).getId());
    }

    @Test
    void getPoolTickets_withAgentAdminRole_returnsOk() {
        Ticket t = Ticket.builder().id(3002L).title("Server issue").description("CPU high")
                .priority("MEDIUM").status("NEW").productId(10L).customerId("customer-1").build();

        Page<Ticket> page = new PageImpl<>(List.of(t));
        when(ticketService.getPoolTicketsFiltered(eq("admin-1"), eq(List.of("ADMIN")),
                any(TicketFilterDTO.class), any(Pageable.class))).thenReturn(page);
        when(userRepository.findById("customer-1")).thenReturn(Optional.of(
                User.builder().id("customer-1").fullName("Customer One").email("c1@example.com").role("CUSTOMER").build()));
        when(productRepository.findById(10L)).thenReturn(Optional.of(Product.builder().id(10L).name("ERP").build()));
        when(ticketService.getSlaTimerInfo(t)).thenReturn(Map.<String, Object>of("deadlineTs", 999L));

        ResponseEntity<Page<TicketResponseDTO>> response = ticketController.getPoolTickets(
                jwtWithRole("admin-1", "ADMIN"),
                0, 20, "createdAt", "desc",
                null, null, null, null, null, null, null, null);

        assertEquals(200, response.getStatusCode().value());
        assertEquals(1, response.getBody().getContent().size());
        assertEquals(3002L, response.getBody().getContent().get(0).getId());
    }

    // -----------------------------------------------------------------------
    // getMyAssignedTickets
    // -----------------------------------------------------------------------

    @Test
    void getMyAssignedTickets_returnsAgentTickets() {
        Ticket t1 = Ticket.builder().id(9001L).title("T1").description("D1")
                .priority("LOW").status("IN_PROGRESS").customerId("c1").build();
        Page<Ticket> page = new PageImpl<>(List.of(t1));
        when(ticketService.getAgentClaimedTicketsFiltered(eq("agent-1"),
                any(TicketFilterDTO.class), any(Pageable.class))).thenReturn(page);

        ResponseEntity<Page<TicketResponseDTO>> response = ticketController.getMyAssignedTickets(
                jwtWithRole("agent-1", "AGENT"),
                0, 20, "createdAt", "desc",
                null, null, null, null, null, null, null, null, null);

        assertEquals(200, response.getStatusCode().value());
        assertEquals(1, response.getBody().getContent().size());
        assertEquals(9001L, response.getBody().getContent().get(0).getId());
    }

    // -----------------------------------------------------------------------
    // claimTicket
    // -----------------------------------------------------------------------

    @Test
    void claimTicket_withAgentRole_returnsClaimedTicket() {
        Ticket claimed = Ticket.builder().id(9001L).title("Claim me").description("desc")
                .priority("LOW").status("IN_PROGRESS").productId(10L).customerId("customer-1").build();

        when(ticketService.claimTicket(9001L, "agent-1")).thenReturn(claimed);
        when(userRepository.findById("customer-1")).thenReturn(Optional.of(
                User.builder().id("customer-1").fullName("Customer One").email("c1@example.com").role("CUSTOMER").build()));
        when(productRepository.findById(10L)).thenReturn(Optional.of(Product.builder().id(10L).name("ERP").build()));
        when(ticketService.getSlaTimerInfo(claimed)).thenReturn(Map.<String, Object>of("deadlineTs", 777L));

        ResponseEntity<TicketResponseDTO> response = ticketController.claimTicket(9001L, jwtWithRole("agent-1", "AGENT"));

        assertEquals(200, response.getStatusCode().value());
        assertEquals(9001L, response.getBody().getId());
        assertEquals("IN_PROGRESS", response.getBody().getStatus());
    }

    @Test
    void claimTicket_withAgentAdminRole_returnsClaimedTicket() {
        Ticket claimed = Ticket.builder().id(9002L).title("Claim me").description("desc")
                .priority("LOW").status("IN_PROGRESS").productId(10L).customerId("customer-1").build();

        when(ticketService.claimTicket(9002L, "admin-1")).thenReturn(claimed);
        when(userRepository.findById("customer-1")).thenReturn(Optional.of(
                User.builder().id("customer-1").fullName("Customer One").email("c1@example.com").role("CUSTOMER").build()));
        when(productRepository.findById(10L)).thenReturn(Optional.of(Product.builder().id(10L).name("ERP").build()));
        when(ticketService.getSlaTimerInfo(claimed)).thenReturn(Map.<String, Object>of("deadlineTs", 777L));

        ResponseEntity<TicketResponseDTO> response = ticketController.claimTicket(9002L, jwtWithRole("admin-1", "ADMIN"));

        assertEquals(200, response.getStatusCode().value());
        assertEquals(9002L, response.getBody().getId());
        assertEquals("IN_PROGRESS", response.getBody().getStatus());
    }

    // -----------------------------------------------------------------------
    // updateStatus
    // -----------------------------------------------------------------------

    @Test
    void updateStatus_withAgentRole_returnsOk() {
        Ticket updated = Ticket.builder().id(4001L).title("Network issue").description("Packet loss")
                .priority("HIGH").status("IN_PROGRESS").productId(10L).customerId("customer-1").build();

        when(ticketService.updateTicketStatus(4001L, "IN_PROGRESS", null, null, "agent-1", List.of("AGENT"))).thenReturn(updated);
        when(userRepository.findById("customer-1")).thenReturn(Optional.of(
                User.builder().id("customer-1").fullName("Customer One").email("c1@example.com").role("CUSTOMER").build()));
        when(productRepository.findById(10L)).thenReturn(Optional.of(Product.builder().id(10L).name("ERP").build()));
        when(ticketService.getSlaTimerInfo(updated)).thenReturn(Map.<String, Object>of("deadlineTs", 888L));

        StatusUpdateRequestDTO body = StatusUpdateRequestDTO.builder().status("IN_PROGRESS").build();
        ResponseEntity<TicketResponseDTO> response = ticketController.updateStatus(
                4001L, body, jwtWithRole("agent-1", "AGENT"));

        assertEquals(200, response.getStatusCode().value());
        assertEquals(4001L, response.getBody().getId());
        assertEquals("IN_PROGRESS", response.getBody().getStatus());
    }

    @Test
    void updateStatus_withAgentAdminRole_returnsOk() {
        Ticket updated = Ticket.builder().id(4002L).title("Network issue").description("Packet loss")
                .priority("HIGH").status("IN_PROGRESS").productId(10L).customerId("customer-1").build();

        when(ticketService.updateTicketStatus(4002L, "IN_PROGRESS", null, null, "admin-1", List.of("ADMIN"))).thenReturn(updated);
        when(userRepository.findById("customer-1")).thenReturn(Optional.of(
                User.builder().id("customer-1").fullName("Customer One").email("c1@example.com").role("CUSTOMER").build()));
        when(productRepository.findById(10L)).thenReturn(Optional.of(Product.builder().id(10L).name("ERP").build()));
        when(ticketService.getSlaTimerInfo(updated)).thenReturn(Map.<String, Object>of("deadlineTs", 888L));

        StatusUpdateRequestDTO body = StatusUpdateRequestDTO.builder().status("IN_PROGRESS").build();
        ResponseEntity<TicketResponseDTO> response = ticketController.updateStatus(
                4002L, body, jwtWithRole("admin-1", "ADMIN"));

        assertEquals(200, response.getStatusCode().value());
        assertEquals(4002L, response.getBody().getId());
        assertEquals("IN_PROGRESS", response.getBody().getStatus());
    }

    @Test
    void updateStatus_withEmptyRoles() {
        Ticket updated = Ticket.builder().id(4002L).title("Network issue").description("Packet loss")
                .priority("HIGH").status("IN_PROGRESS").build();

        when(ticketService.updateTicketStatus(4002L, "IN_PROGRESS", null, null, "c1", List.of())).thenReturn(updated);

        Jwt jwt = org.mockito.Mockito.mock(Jwt.class);
        when(jwt.getSubject()).thenReturn("c1");
        lenient().when(jwt.getClaimAsMap("realm_access")).thenReturn(null);

        StatusUpdateRequestDTO body = StatusUpdateRequestDTO.builder().status("IN_PROGRESS").build();
        ResponseEntity<TicketResponseDTO> response = ticketController.updateStatus(4002L, body, jwt);
        assertEquals(200, response.getStatusCode().value());
        assertEquals("Unknown", response.getBody().getCustomerName());
        assertEquals("Unknown", response.getBody().getProductName());
        org.junit.jupiter.api.Assertions.assertTrue(response.getBody().getClaimers().isEmpty());
    }

    // -----------------------------------------------------------------------
    // deleteTicket / getSlaTimer / getTicket
    // -----------------------------------------------------------------------

    @Test
    void deleteTicket_withManagerRole_returnsNoContent() {
        ResponseEntity<Void> response = ticketController.deleteTicket(5001L);
        assertEquals(204, response.getStatusCode().value());
        verify(ticketService).deleteTicket(5001L);
    }

    @Test
    void getSlaTimer_withAgentAdminRole_returnsTimerPayload() {
        Ticket ticket = Ticket.builder().id(7001L).title("SLA ticket").description("desc")
                .priority("MEDIUM").status("IN_PROGRESS").productId(10L).customerId("customer-1").build();

        when(ticketService.getTicketWithAuth(7001L, "admin-1", List.of("ADMIN"))).thenReturn(ticket);
        when(ticketService.getSlaTimerInfo(ticket)).thenReturn(Map.<String, Object>of("deadlineTs", 555L));

        ResponseEntity<Map<String, Object>> response = ticketController.getSlaTimer(7001L, jwtWithRole("admin-1", "ADMIN"));

        assertEquals(200, response.getStatusCode().value());
        assertEquals(555L, response.getBody().get("deadlineTs"));
    }

    @Test
    void getSlaTimer_withManagerRole_returnsTimerPayload() {
        Ticket ticket = Ticket.builder().id(7002L).title("SLA ticket").description("desc")
                .priority("MEDIUM").status("IN_PROGRESS").productId(10L).customerId("customer-1").build();

        when(ticketService.getTicketWithAuth(7002L, "manager-1", List.of("MANAGER"))).thenReturn(ticket);
        when(ticketService.getSlaTimerInfo(ticket)).thenReturn(Map.<String, Object>of("deadlineTs", 556L));

        ResponseEntity<Map<String, Object>> response = ticketController.getSlaTimer(7002L, jwtWithRole("manager-1", "MANAGER"));

        assertEquals(200, response.getStatusCode().value());
        assertEquals(556L, response.getBody().get("deadlineTs"));
    }

    @Test
    void getTicket_returnsTicketDetail() {
        Ticket t1 = Ticket.builder().id(10001L).title("T1").description("D1")
                .priority("LOW").status("NEW").productId(1L).customerId("c1").build();
        when(ticketService.getTicketWithAuth(10001L, "customer-1", List.of("CUSTOMER"))).thenReturn(t1);

        ResponseEntity<TicketResponseDTO> response = ticketController.getTicket(10001L, jwtWithRole("customer-1", "CUSTOMER"));

        assertEquals(200, response.getStatusCode().value());
        assertEquals(10001L, response.getBody().getId());
        assertEquals("Unknown", response.getBody().getProductName());
    }

    // -----------------------------------------------------------------------
    // getTeamTickets
    // -----------------------------------------------------------------------

    @Test
    void getTeamTickets_withAgentRole_returnsOk() {
        Ticket t = Ticket.builder().id(6001L).title("Team ticket").description("desc")
                .priority("HIGH").status("IN_PROGRESS").productId(10L).customerId("customer-1").build();
        Page<Ticket> page = new PageImpl<>(List.of(t));
        when(ticketService.getTeamTicketsFiltered(eq("agent-1"), eq(List.of("AGENT")),
                any(TicketFilterDTO.class), any(Pageable.class))).thenReturn(page);
        when(userRepository.findById("customer-1")).thenReturn(Optional.of(
                User.builder().id("customer-1").fullName("Customer One").email("c1@example.com").role("CUSTOMER").build()));
        when(productRepository.findById(10L)).thenReturn(Optional.of(Product.builder().id(10L).name("ERP").build()));
        when(ticketService.getSlaTimerInfo(t)).thenReturn(Map.<String, Object>of("deadlineTs", 123L));

        ResponseEntity<Page<TicketResponseDTO>> response = ticketController.getTeamTickets(
                jwtWithRole("agent-1", "AGENT"),
                0, 20, "createdAt", "desc",
                null, null, null, null, null, null, null, null, null);

        assertEquals(200, response.getStatusCode().value());
        assertEquals(1, response.getBody().getContent().size());
        assertEquals(6001L, response.getBody().getContent().get(0).getId());
    }

    @Test
    void getTeamTickets_withAgentAdminRole_returnsOk() {
        Page<Ticket> page = new PageImpl<>(List.of());
        when(ticketService.getTeamTicketsFiltered(eq("admin-1"), eq(List.of("ADMIN")),
                any(TicketFilterDTO.class), any(Pageable.class))).thenReturn(page);

        ResponseEntity<Page<TicketResponseDTO>> response = ticketController.getTeamTickets(
                jwtWithRole("admin-1", "ADMIN"),
                0, 20, "createdAt", "desc",
                null, null, null, null, null, null, null, null, null);

        assertEquals(200, response.getStatusCode().value());
        assertEquals(0, response.getBody().getContent().size());
    }

    // -----------------------------------------------------------------------
    // getTicketsByProduct
    // -----------------------------------------------------------------------

    @Test
    void getTicketsByProduct_withAgentRole_returnsOk() {
        Ticket t = Ticket.builder().id(7001L).title("Product ticket").description("desc")
                .priority("LOW").status("NEW").productId(10L).customerId("customer-1").build();
        Page<Ticket> page = new PageImpl<>(List.of(t));
        when(ticketService.getTicketsByProductFiltered(eq(10L), eq("agent-1"), eq(List.of("AGENT")),
                any(TicketFilterDTO.class), any(Pageable.class))).thenReturn(page);
        when(userRepository.findById("customer-1")).thenReturn(Optional.of(
                User.builder().id("customer-1").fullName("Customer One").email("c1@example.com").role("CUSTOMER").build()));
        when(productRepository.findById(10L)).thenReturn(Optional.of(Product.builder().id(10L).name("ERP").build()));
        when(ticketService.getSlaTimerInfo(t)).thenReturn(Map.<String, Object>of("deadlineTs", 456L));

        ResponseEntity<Page<TicketResponseDTO>> response = ticketController.getTicketsByProduct(
                10L,
                jwtWithRole("agent-1", "AGENT"),
                0, 20, "createdAt", "desc",
                null, null, null, null, null, null, null, null);

        assertEquals(200, response.getStatusCode().value());
        assertEquals(1, response.getBody().getContent().size());
        assertEquals(7001L, response.getBody().getContent().get(0).getId());
    }

    // -----------------------------------------------------------------------
    // assignTicket
    // -----------------------------------------------------------------------

    @Test
    void assignTicket_withAgentAdminRole_returnsOk() {
        Ticket assigned = Ticket.builder().id(8001L).title("Assign me").description("desc")
                .priority("HIGH").status("IN_PROGRESS").productId(10L).customerId("customer-1").build();
        AssignTicketRequestDTO request = AssignTicketRequestDTO.builder()
                .targetAgentId("agent-1").note("Admin assigned").build();

        when(ticketService.assignTicket(8001L, "agent-1", "admin-1", "Admin assigned")).thenReturn(assigned);
        when(userRepository.findById("customer-1")).thenReturn(Optional.of(
                User.builder().id("customer-1").fullName("Customer One").email("c1@example.com").role("CUSTOMER").build()));
        when(productRepository.findById(10L)).thenReturn(Optional.of(Product.builder().id(10L).name("ERP").build()));
        when(ticketService.getSlaTimerInfo(assigned)).thenReturn(Map.<String, Object>of("deadlineTs", 789L));

        ResponseEntity<TicketResponseDTO> response = ticketController.assignTicket(8001L, request, jwtWithRole("admin-1", "ADMIN"));

        assertEquals(200, response.getStatusCode().value());
        assertEquals(8001L, response.getBody().getId());
    }

    // -----------------------------------------------------------------------
    // unclaimTicket
    // -----------------------------------------------------------------------

    @Test
    void unclaimTicket_withAgentRole_returnsOk() {
        Ticket unclaimed = Ticket.builder().id(9003L).title("Unclaim me").description("desc")
                .priority("LOW").status("NEW").productId(10L).customerId("customer-1").build();
        UnclaimRequestDTO dto = new UnclaimRequestDTO();
        dto.setReasonCode("WORKLOAD");
        dto.setNote("dropping this ticket");

        when(ticketService.unclaimTicket(9003L, "agent-1", "WORKLOAD", "dropping this ticket")).thenReturn(unclaimed);
        when(userRepository.findById("customer-1")).thenReturn(Optional.of(
                User.builder().id("customer-1").fullName("Customer One").email("c1@example.com").role("CUSTOMER").build()));
        when(productRepository.findById(10L)).thenReturn(Optional.of(Product.builder().id(10L).name("ERP").build()));
        when(ticketService.getSlaTimerInfo(unclaimed)).thenReturn(Map.<String, Object>of("deadlineTs", 111L));

        ResponseEntity<TicketResponseDTO> response = ticketController.unclaimTicket(9003L, dto, jwtWithRole("agent-1", "AGENT"));

        assertEquals(200, response.getStatusCode().value());
        assertEquals(9003L, response.getBody().getId());
    }

    // -----------------------------------------------------------------------
    // Sort direction "asc" branches
    // -----------------------------------------------------------------------

    @Test
    void getTickets_withAgentRole_callsTeamFiltered() {
        Page<Ticket> page = new PageImpl<>(List.of());
        when(ticketService.getTeamTicketsFiltered(eq("agent-1"), eq(List.of("AGENT")),
                any(TicketFilterDTO.class), any(Pageable.class))).thenReturn(page);

        ResponseEntity<Page<TicketResponseDTO>> response = ticketController.getTickets(
                jwtWithRole("agent-1", "AGENT"),
                0, 20, "createdAt", "desc",
                null, null, null, null, null, null, null, null, null);

        assertEquals(200, response.getStatusCode().value());
    }

    @Test
    void getTickets_ascSort_triggersAscendingBranch() {
        Page<Ticket> page = new PageImpl<>(List.of());
        when(ticketService.getTeamTicketsFiltered(eq("admin-1"), eq(List.of("ADMIN")),
                any(TicketFilterDTO.class), any(Pageable.class))).thenReturn(page);

        ResponseEntity<Page<TicketResponseDTO>> response = ticketController.getTickets(
                jwtWithRole("admin-1", "ADMIN"),
                0, 20, "createdAt", "asc",
                null, null, null, null, null, null, null, null, null);

        assertEquals(200, response.getStatusCode().value());
    }

    @Test
    void getMyAssignedTickets_ascSort_triggersAscendingBranch() {
        Page<Ticket> page = new PageImpl<>(List.of());
        when(ticketService.getAgentClaimedTicketsFiltered(eq("agent-1"),
                any(TicketFilterDTO.class), any(Pageable.class))).thenReturn(page);

        ResponseEntity<Page<TicketResponseDTO>> response = ticketController.getMyAssignedTickets(
                jwtWithRole("agent-1", "AGENT"),
                0, 20, "createdAt", "asc",
                null, null, null, null, null, null, null, null, null);

        assertEquals(200, response.getStatusCode().value());
    }

    @Test
    void getTeamTickets_ascSort_triggersAscendingBranch() {
        Page<Ticket> page = new PageImpl<>(List.of());
        when(ticketService.getTeamTicketsFiltered(eq("agent-1"), eq(List.of("AGENT")),
                any(TicketFilterDTO.class), any(Pageable.class))).thenReturn(page);

        ResponseEntity<Page<TicketResponseDTO>> response = ticketController.getTeamTickets(
                jwtWithRole("agent-1", "AGENT"),
                0, 20, "createdAt", "asc",
                null, null, null, null, null, null, null, null, null);

        assertEquals(200, response.getStatusCode().value());
    }

    @Test
    void getTicketsByProduct_ascSort_triggersAscendingBranch() {
        Page<Ticket> page = new PageImpl<>(List.of());
        when(ticketService.getTicketsByProductFiltered(eq(10L), eq("agent-1"), eq(List.of("AGENT")),
                any(TicketFilterDTO.class), any(Pageable.class))).thenReturn(page);

        ResponseEntity<Page<TicketResponseDTO>> response = ticketController.getTicketsByProduct(
                10L,
                jwtWithRole("agent-1", "AGENT"),
                0, 20, "createdAt", "asc",
                null, null, null, null, null, null, null, null);

        assertEquals(200, response.getStatusCode().value());
    }

    @Test
    void getPoolTickets_ascSort_triggersAscendingBranch() {
        Page<Ticket> page = new PageImpl<>(List.of());
        when(ticketService.getPoolTicketsFiltered(eq("agent-1"), eq(List.of("AGENT")),
                any(TicketFilterDTO.class), any(Pageable.class))).thenReturn(page);

        ResponseEntity<Page<TicketResponseDTO>> response = ticketController.getPoolTickets(
                jwtWithRole("agent-1", "AGENT"),
                0, 20, "createdAt", "asc",
                null, null, null, null, null, null, null, null);

        assertEquals(200, response.getStatusCode().value());
    }

    // -----------------------------------------------------------------------
    // closeTicket and updatePriority handlers
    // -----------------------------------------------------------------------

    @Test
    void closeTicket_returnsDto() {
        Ticket closed = Ticket.builder().id(7000L).title("t").description("d")
                .priority("HIGH").status("CLOSED").productId(10L).customerId("customer-1").build();
        com.ticketsystem.it_service_backend.dto.CloseTicketRequestDTO body =
                new com.ticketsystem.it_service_backend.dto.CloseTicketRequestDTO();
        body.setReasonCode("RESOLVED_CONFIRMED");
        body.setNote("done");
        when(ticketService.closeTicket(7000L, "RESOLVED_CONFIRMED", "done", "agent-1", List.of("AGENT")))
                .thenReturn(closed);
        when(userRepository.findById("customer-1")).thenReturn(Optional.of(
                User.builder().id("customer-1").fullName("C").email("c@x").role("CUSTOMER").build()));
        when(productRepository.findById(10L)).thenReturn(Optional.of(Product.builder().id(10L).name("ERP").build()));
        when(ticketService.getSlaTimerInfo(closed)).thenReturn(Map.<String, Object>of("deadlineTs", 1L));

        ResponseEntity<TicketResponseDTO> response =
                ticketController.closeTicket(7000L, body, jwtWithRole("agent-1", "AGENT"));

        assertEquals(200, response.getStatusCode().value());
        assertEquals(7000L, response.getBody().getId());
    }

    @Test
    void updatePriority_returnsDto() {
        Ticket updated = Ticket.builder().id(7100L).title("t").description("d")
                .priority("CRITICAL").status("IN_PROGRESS").productId(10L).customerId("customer-1").build();
        when(ticketService.updateTicketPriority(7100L, "CRITICAL", "CUSTOMER_IMPACT", null, "agent-1", List.of("AGENT")))
                .thenReturn(updated);
        when(userRepository.findById("customer-1")).thenReturn(Optional.of(
                User.builder().id("customer-1").fullName("C").email("c@x").role("CUSTOMER").build()));
        when(productRepository.findById(10L)).thenReturn(Optional.of(Product.builder().id(10L).name("ERP").build()));
        when(ticketService.getSlaTimerInfo(updated)).thenReturn(Map.<String, Object>of("deadlineTs", 1L));

        com.ticketsystem.it_service_backend.dto.PriorityChangeRequestDTO dto =
                com.ticketsystem.it_service_backend.dto.PriorityChangeRequestDTO.builder()
                        .priority("CRITICAL").reasonCode("CUSTOMER_IMPACT").build();
        ResponseEntity<TicketResponseDTO> response =
                ticketController.updatePriority(7100L, dto, jwtWithRole("agent-1", "AGENT"));

        assertEquals(200, response.getStatusCode().value());
        assertEquals("CRITICAL", response.getBody().getPriority());
    }

    @Test
    void deleteTicket_returnsNoContent() {
        ResponseEntity<Void> response = ticketController.deleteTicket(7200L);
        assertEquals(204, response.getStatusCode().value());
        verify(ticketService).deleteTicket(7200L);
    }

    // -----------------------------------------------------------------------
    // Helper
    // -----------------------------------------------------------------------

    private Jwt jwtWithRole(String subject, String role) {
        Jwt jwt = org.mockito.Mockito.mock(Jwt.class);
        when(jwt.getSubject()).thenReturn(subject);
        lenient().when(jwt.getClaimAsMap("realm_access")).thenReturn(Map.of("roles", List.of(role)));
        return jwt;
    }
}
