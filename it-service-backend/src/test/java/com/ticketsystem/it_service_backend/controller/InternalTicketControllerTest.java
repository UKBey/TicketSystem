package com.ticketsystem.it_service_backend.controller;

import com.ticketsystem.it_service_backend.dto.TicketResponseDTO;
import com.ticketsystem.it_service_backend.entity.Comment;
import com.ticketsystem.it_service_backend.entity.KnownIssue;
import com.ticketsystem.it_service_backend.entity.Product;
import com.ticketsystem.it_service_backend.entity.Ticket;
import com.ticketsystem.it_service_backend.entity.TicketAuditLog;
import com.ticketsystem.it_service_backend.entity.TicketClaim;
import com.ticketsystem.it_service_backend.entity.TicketWorklog;
import com.ticketsystem.it_service_backend.entity.User;
import com.ticketsystem.it_service_backend.repository.CommentRepository;
import com.ticketsystem.it_service_backend.repository.KnownIssueRepository;
import com.ticketsystem.it_service_backend.repository.ProductRepository;
import com.ticketsystem.it_service_backend.repository.TicketAuditLogRepository;
import com.ticketsystem.it_service_backend.repository.TicketClaimRepository;
import com.ticketsystem.it_service_backend.repository.UserRepository;
import com.ticketsystem.it_service_backend.repository.WorklogRepository;
import com.ticketsystem.it_service_backend.service.TicketService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InternalTicketControllerTest {

    @Mock private TicketService ticketService;
    @Mock private CommentRepository commentRepository;
    @Mock private WorklogRepository worklogRepository;
    @Mock private TicketAuditLogRepository ticketAuditLogRepository;
    @Mock private TicketClaimRepository ticketClaimRepository;
    @Mock private UserRepository userRepository;
    @Mock private ProductRepository productRepository;
    @Mock private KnownIssueRepository knownIssueRepository;

    @InjectMocks private InternalTicketController controller;

    @Test
    void getFullTicketData_assemblesFullPayload() {
        Long ticketId = 42L;
        Ticket ticket = Ticket.builder()
                .id(ticketId)
                .title("Cannot login")
                .description("Login fails")
                .priority("HIGH")
                .status("IN_PROGRESS")
                .productId(10L)
                .topicId(5L)
                .topicNameSnapshot("Auth")
                .customerId("customer-1")
                .createdAt(ZonedDateTime.now())
                .build();

        when(ticketService.getTicketById(ticketId)).thenReturn(ticket);
        when(userRepository.findById("customer-1")).thenReturn(Optional.of(
                User.builder().id("customer-1").fullName("Alice Customer").build()));
        when(productRepository.findById(10L)).thenReturn(Optional.of(
                Product.builder().id(10L).name("CRM").isActive(true).build()));

        Ticket claimTicket = Ticket.builder().id(ticketId).build();
        TicketClaim claim = TicketClaim.builder()
                .ticket(claimTicket)
                .agentId("agent-1")
                .claimedAt(LocalDateTime.now())
                .build();
        when(ticketClaimRepository.findByTicketId(ticketId)).thenReturn(List.of(claim));
        when(userRepository.findById("agent-1")).thenReturn(Optional.of(
                User.builder().id("agent-1").fullName("Bob Agent").build()));

        when(ticketService.getSlaTimerInfo(ticket)).thenReturn(Map.of("deadlineTs", 1234L));

        TicketAuditLog audit = TicketAuditLog.builder()
                .id(1L).ticket(claimTicket).actorId("agent-1")
                .actionType("CLAIM").note(null).previousState("NEW").newState("IN_PROGRESS")
                .createdAt(ZonedDateTime.now()).build();
        when(ticketAuditLogRepository.findByTicketIdOrderByCreatedAtDesc(ticketId)).thenReturn(List.of(audit));

        Comment comment = Comment.builder()
                .id(1L).ticket(claimTicket).authorId("customer-1").message("hi").type("EXTERNAL")
                .build();
        when(commentRepository.findByTicketIdOrderByCreatedAtAsc(ticketId)).thenReturn(List.of(comment));

        TicketWorklog worklog = TicketWorklog.builder()
                .id(1L).ticketId(ticketId).agentId("agent-1").minutes(30).description("debug").build();
        when(worklogRepository.findByTicketId(ticketId)).thenReturn(List.of(worklog));

        // Bilet ürün 10 / topic 5'e ait: topic-5 + ürün geneli (topic'siz) dahil edilmeli,
        // başka bir topic'e ait kayıt (topic 999) elenmelidir.
        KnownIssue topicIssue = KnownIssue.builder()
                .id(1L).productId(10L).topicId(5L).title("Auth token expires")
                .content("Re-issue the token").isActive(true).build();
        KnownIssue productWideIssue = KnownIssue.builder()
                .id(2L).productId(10L).topicId(null).title("CRM is slow")
                .content("Clear the cache").isActive(true).build();
        KnownIssue otherTopicIssue = KnownIssue.builder()
                .id(3L).productId(10L).topicId(999L).title("Unrelated")
                .content("n/a").isActive(true).build();
        when(knownIssueRepository.findByProductIdAndIsActiveTrueOrderByCreatedAtDesc(10L))
                .thenReturn(List.of(topicIssue, productWideIssue, otherTopicIssue));

        ResponseEntity<Map<String, Object>> response = controller.getFullTicketData(ticketId);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        Map<String, Object> body = response.getBody();
        assertThat(body).containsKeys("ticket", "comments", "worklogs", "knownIssues");
        assertThat((List<?>) body.get("knownIssues")).hasSize(2);

        TicketResponseDTO dto = (TicketResponseDTO) body.get("ticket");
        assertThat(dto.getId()).isEqualTo(ticketId);
        assertThat(dto.getCustomerName()).isEqualTo("Alice Customer");
        assertThat(dto.getProductName()).isEqualTo("CRM");
        assertThat(dto.getClaimers()).hasSize(1);
        assertThat(dto.getClaimers().get(0).getAgentName()).isEqualTo("Bob Agent");
        assertThat(dto.getAuditLogs()).hasSize(1);
        assertThat(dto.getSlaInfo()).containsEntry("deadlineTs", 1234L);
        assertThat((List<?>) body.get("comments")).hasSize(1);
        assertThat((List<?>) body.get("worklogs")).hasSize(1);
    }

    @Test
    void getFullTicketData_missingUserAndProduct_fallsBackToUnknown() {
        Long ticketId = 43L;
        Ticket ticket = Ticket.builder()
                .id(ticketId).title("t").description("d").priority("LOW").status("NEW")
                .productId(99L).customerId("missing").build();

        when(ticketService.getTicketById(ticketId)).thenReturn(ticket);
        when(userRepository.findById("missing")).thenReturn(Optional.empty());
        when(productRepository.findById(99L)).thenReturn(Optional.empty());
        when(ticketClaimRepository.findByTicketId(ticketId)).thenReturn(List.of());
        when(ticketAuditLogRepository.findByTicketIdOrderByCreatedAtDesc(ticketId)).thenReturn(List.of());
        when(commentRepository.findByTicketIdOrderByCreatedAtAsc(ticketId)).thenReturn(List.of());
        when(worklogRepository.findByTicketId(ticketId)).thenReturn(List.of());
        when(ticketService.getSlaTimerInfo(ticket)).thenReturn(Map.of());
        when(knownIssueRepository.findByProductIdAndIsActiveTrueOrderByCreatedAtDesc(99L)).thenReturn(List.of());

        ResponseEntity<Map<String, Object>> response = controller.getFullTicketData(ticketId);

        TicketResponseDTO dto = (TicketResponseDTO) response.getBody().get("ticket");
        assertThat(dto.getCustomerName()).isEqualTo("Unknown");
        assertThat(dto.getProductName()).isEqualTo("Unknown");
    }

    @Test
    void getFullTicketData_nullProductId_skipsProductLookupAndUsesUnknown() {
        Long ticketId = 44L;
        Ticket ticket = Ticket.builder()
                .id(ticketId).title("t").description("d").priority("LOW").status("NEW")
                .productId(null).customerId("customer-1").build();

        when(ticketService.getTicketById(ticketId)).thenReturn(ticket);
        when(userRepository.findById("customer-1")).thenReturn(Optional.of(
                User.builder().id("customer-1").fullName("Alice").build()));
        when(ticketClaimRepository.findByTicketId(ticketId)).thenReturn(List.of());
        when(ticketAuditLogRepository.findByTicketIdOrderByCreatedAtDesc(ticketId)).thenReturn(List.of());
        when(commentRepository.findByTicketIdOrderByCreatedAtAsc(ticketId)).thenReturn(List.of());
        when(worklogRepository.findByTicketId(ticketId)).thenReturn(List.of());
        when(ticketService.getSlaTimerInfo(ticket)).thenReturn(Map.of());

        ResponseEntity<Map<String, Object>> response = controller.getFullTicketData(ticketId);

        TicketResponseDTO dto = (TicketResponseDTO) response.getBody().get("ticket");
        assertThat(dto.getProductName()).isEqualTo("Unknown");
    }

    @Test
    void getFullTicketData_auditLogMissingActor_fallsBackToActorId() {
        Long ticketId = 45L;
        Ticket ticket = Ticket.builder()
                .id(ticketId).title("t").description("d").priority("LOW").status("NEW")
                .productId(10L).customerId("customer-1").build();

        when(ticketService.getTicketById(ticketId)).thenReturn(ticket);
        when(userRepository.findById("customer-1")).thenReturn(Optional.of(
                User.builder().id("customer-1").fullName("Alice").build()));
        when(productRepository.findById(10L)).thenReturn(Optional.of(
                Product.builder().id(10L).name("CRM").build()));
        when(ticketClaimRepository.findByTicketId(ticketId)).thenReturn(List.of());

        TicketAuditLog audit = TicketAuditLog.builder()
                .id(1L).ticket(ticket).actorId("ghost-actor").actionType("CREATE")
                .createdAt(ZonedDateTime.now()).build();
        when(ticketAuditLogRepository.findByTicketIdOrderByCreatedAtDesc(ticketId)).thenReturn(List.of(audit));
        when(userRepository.findById("ghost-actor")).thenReturn(Optional.empty());

        when(commentRepository.findByTicketIdOrderByCreatedAtAsc(ticketId)).thenReturn(List.of());
        when(worklogRepository.findByTicketId(ticketId)).thenReturn(List.of());
        when(ticketService.getSlaTimerInfo(ticket)).thenReturn(Map.of());
        when(knownIssueRepository.findByProductIdAndIsActiveTrueOrderByCreatedAtDesc(10L)).thenReturn(List.of());

        ResponseEntity<Map<String, Object>> response = controller.getFullTicketData(ticketId);

        TicketResponseDTO dto = (TicketResponseDTO) response.getBody().get("ticket");
        assertThat(dto.getAuditLogs().get(0).getActorName()).isEqualTo("ghost-actor");
    }
}
