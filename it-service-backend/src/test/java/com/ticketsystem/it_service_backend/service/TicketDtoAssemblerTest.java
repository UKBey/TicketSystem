package com.ticketsystem.it_service_backend.service;

import com.ticketsystem.it_service_backend.dto.KnownIssueDTO;
import com.ticketsystem.it_service_backend.dto.TicketResponseDTO;
import com.ticketsystem.it_service_backend.entity.Csat;
import com.ticketsystem.it_service_backend.entity.Product;
import com.ticketsystem.it_service_backend.entity.Ticket;
import com.ticketsystem.it_service_backend.entity.TicketAuditLog;
import com.ticketsystem.it_service_backend.entity.TicketClaim;
import com.ticketsystem.it_service_backend.repository.CsatRepository;
import com.ticketsystem.it_service_backend.repository.ProductRepository;
import com.ticketsystem.it_service_backend.repository.TicketAuditLogRepository;
import com.ticketsystem.it_service_backend.repository.TicketClaimRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the ticket DTO assembly previously embedded in the controllers:
 * customer/product/claimer/audit-actor name resolution, CSAT visibility rules,
 * "Unknown"/actorId fallbacks, and the internal full-ticket bundle wiring.
 */
@ExtendWith(MockitoExtension.class)
class TicketDtoAssemblerTest {

    @Mock private TicketService ticketService;
    @Mock private UserService userService;
    @Mock private CommentService commentService;
    @Mock private WorklogService worklogService;
    @Mock private KnownIssueService knownIssueService;
    @Mock private ProductRepository productRepository;
    @Mock private TicketClaimRepository ticketClaimRepository;
    @Mock private TicketAuditLogRepository ticketAuditLogRepository;
    @Mock private CsatRepository csatRepository;

    @InjectMocks private TicketDtoAssembler assembler;

    private Ticket sampleTicket(Long id, Long productId, Long topicId) {
        return Ticket.builder()
                .id(id).title("Cannot login").description("Login fails")
                .priority("HIGH").status("IN_PROGRESS")
                .productId(productId).topicId(topicId).customerId("customer-1")
                .createdAt(ZonedDateTime.now())
                .build();
    }

    // -----------------------------------------------------------------------
    // toDto — role-aware
    // -----------------------------------------------------------------------

    @Test
    void toDto_agentRole_resolvesNamesAndHidesCsatAudit() {
        Ticket ticket = sampleTicket(42L, 10L, 5L);

        TicketClaim claim = TicketClaim.builder().agentId("agent-1").claimedAt(LocalDateTime.now()).build();
        when(ticketClaimRepository.findByTicketId(42L)).thenReturn(List.of(claim));

        TicketAuditLog claimLog = TicketAuditLog.builder().id(1L).actorId("agent-1")
                .actionType("CLAIM").createdAt(ZonedDateTime.now()).build();
        TicketAuditLog csatLog = TicketAuditLog.builder().id(2L).actorId("customer-1")
                .actionType("CSAT_SUBMITTED").createdAt(ZonedDateTime.now()).build();
        when(ticketAuditLogRepository.findByTicketIdOrderByCreatedAtDesc(42L))
                .thenReturn(List.of(claimLog, csatLog));

        when(userService.getDisplayNames(any())).thenReturn(
                Map.of("customer-1", "Alice Customer", "agent-1", "Bob Agent"));
        when(productRepository.findById(10L)).thenReturn(Optional.of(Product.builder().id(10L).nameEn("CRM").build()));
        when(ticketService.getSlaTimerInfo(ticket)).thenReturn(Map.of("deadlineTs", 1234L));

        TicketResponseDTO dto = assembler.toDto(ticket, false, List.of("AGENT"));

        assertThat(dto.getCustomerName()).isEqualTo("Alice Customer");
        assertThat(dto.getProductNameEn()).isEqualTo("CRM");
        assertThat(dto.getClaimers()).hasSize(1);
        assertThat(dto.getClaimers().get(0).getAgentName()).isEqualTo("Bob Agent");
        // CSAT_SUBMITTED audit entry hidden from non-privileged (agent) callers.
        assertThat(dto.getAuditLogs()).hasSize(1);
        assertThat(dto.getAuditLogs().get(0).getActorName()).isEqualTo("Bob Agent");
        assertThat(dto.getSlaInfo()).containsEntry("deadlineTs", 1234L);
        assertThat(dto.getCsatRating()).isNull();
    }

    @Test
    void toDto_adminRole_keepsCsatAuditAndExposesRating() {
        Ticket ticket = sampleTicket(43L, 10L, null);

        when(ticketClaimRepository.findByTicketId(43L)).thenReturn(List.of());
        TicketAuditLog csatLog = TicketAuditLog.builder().id(2L).actorId("customer-1")
                .actionType("CSAT_SUBMITTED").createdAt(ZonedDateTime.now()).build();
        when(ticketAuditLogRepository.findByTicketIdOrderByCreatedAtDesc(43L)).thenReturn(List.of(csatLog));
        when(userService.getDisplayNames(any())).thenReturn(Map.of("customer-1", "Alice Customer"));
        when(productRepository.findById(10L)).thenReturn(Optional.of(Product.builder().id(10L).nameEn("CRM").build()));
        when(ticketService.getSlaTimerInfo(ticket)).thenReturn(Map.of());
        when(csatRepository.findByTicketId(43L)).thenReturn(Optional.of(Csat.builder().rating(5).build()));

        TicketResponseDTO dto = assembler.toDto(ticket, true, List.of("ADMIN"));

        // ADMIN sees the CSAT audit entry and the numeric rating.
        assertThat(dto.getAuditLogs()).hasSize(1);
        assertThat(dto.getCsatRating()).isEqualTo(5);
    }

    @Test
    void toDto_missingUserAndProduct_fallsBackToUnknownUserAndNullProductNames() {
        Ticket ticket = sampleTicket(44L, 99L, null);

        when(ticketClaimRepository.findByTicketId(44L)).thenReturn(List.of());
        when(ticketAuditLogRepository.findByTicketIdOrderByCreatedAtDesc(44L)).thenReturn(List.of());
        when(userService.getDisplayNames(any())).thenReturn(Map.of());
        when(productRepository.findById(99L)).thenReturn(Optional.empty());
        when(ticketService.getSlaTimerInfo(ticket)).thenReturn(Map.of());

        TicketResponseDTO dto = assembler.toDto(ticket, false, List.of("AGENT"));

        assertThat(dto.getCustomerName()).isEqualTo("Unknown");
        // Ürün bulunamadığında ad alanları null kalır; istemci productId'ye düşer.
        assertThat(dto.getProductNameTr()).isNull();
        assertThat(dto.getProductNameEn()).isNull();
    }

    @Test
    void toDto_nullProductId_skipsProductLookupAndLeavesNamesNull() {
        Ticket ticket = sampleTicket(45L, null, null);

        when(ticketClaimRepository.findByTicketId(45L)).thenReturn(List.of());
        when(ticketAuditLogRepository.findByTicketIdOrderByCreatedAtDesc(45L)).thenReturn(List.of());
        when(userService.getDisplayNames(any())).thenReturn(Map.of("customer-1", "Alice"));
        when(ticketService.getSlaTimerInfo(ticket)).thenReturn(Map.of());

        TicketResponseDTO dto = assembler.toDto(ticket, false, List.of("AGENT"));

        assertThat(dto.getProductNameTr()).isNull();
        assertThat(dto.getProductNameEn()).isNull();
    }

    // -----------------------------------------------------------------------
    // toInternalDto
    // -----------------------------------------------------------------------

    @Test
    void toInternalDto_auditMissingActor_fallsBackToActorId_andKeepsAllAudit() {
        Ticket ticket = sampleTicket(46L, 10L, null);

        when(ticketClaimRepository.findByTicketId(46L)).thenReturn(List.of());
        TicketAuditLog ghost = TicketAuditLog.builder().id(1L).actorId("ghost-actor")
                .actionType("CREATE").createdAt(ZonedDateTime.now()).build();
        TicketAuditLog csatLog = TicketAuditLog.builder().id(2L).actorId("customer-1")
                .actionType("CSAT_SUBMITTED").createdAt(ZonedDateTime.now()).build();
        when(ticketAuditLogRepository.findByTicketIdOrderByCreatedAtDesc(46L)).thenReturn(List.of(ghost, csatLog));
        when(userService.getDisplayNames(any())).thenReturn(Map.of("customer-1", "Alice"));
        when(productRepository.findById(10L)).thenReturn(Optional.of(Product.builder().id(10L).nameEn("CRM").build()));
        when(ticketService.getSlaTimerInfo(ticket)).thenReturn(Map.of());

        TicketResponseDTO dto = assembler.toInternalDto(ticket);

        // Internal view keeps every audit entry (incl. CSAT) and never exposes the rating.
        assertThat(dto.getAuditLogs()).hasSize(2);
        assertThat(dto.getAuditLogs().get(0).getActorName()).isEqualTo("ghost-actor");
        assertThat(dto.getCsatRating()).isNull();
    }

    // -----------------------------------------------------------------------
    // buildFullTicketData
    // -----------------------------------------------------------------------

    @Test
    void buildFullTicketData_bundlesAllSections() {
        Long ticketId = 50L;
        Ticket ticket = sampleTicket(ticketId, 10L, 5L);
        when(ticketService.getTicketById(ticketId)).thenReturn(ticket);

        // toInternalDto path stubs
        lenient().when(ticketClaimRepository.findByTicketId(ticketId)).thenReturn(List.of());
        lenient().when(ticketAuditLogRepository.findByTicketIdOrderByCreatedAtDesc(ticketId)).thenReturn(List.of());
        lenient().when(userService.getDisplayNames(any())).thenReturn(Map.of("customer-1", "Alice"));
        lenient().when(productRepository.findById(10L)).thenReturn(Optional.of(Product.builder().id(10L).nameEn("CRM").build()));
        lenient().when(ticketService.getSlaTimerInfo(ticket)).thenReturn(Map.of());

        when(commentService.getAllCommentDtos(ticketId)).thenReturn(List.of());
        when(worklogService.getAllWorklogDtosByTicket(ticketId)).thenReturn(List.of());
        when(knownIssueService.getActiveForTicket(10L, 5L))
                .thenReturn(List.of(KnownIssueDTO.builder().id(1L).title("Auth token expires").build()));

        Map<String, Object> body = assembler.buildFullTicketData(ticketId);

        assertThat(body).containsKeys("ticket", "comments", "worklogs", "knownIssues");
        assertThat(body.get("ticket")).isInstanceOf(TicketResponseDTO.class);
        assertThat((List<?>) body.get("knownIssues")).hasSize(1);
    }
}
