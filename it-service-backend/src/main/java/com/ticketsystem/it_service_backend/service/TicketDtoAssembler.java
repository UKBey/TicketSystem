package com.ticketsystem.it_service_backend.service;

import com.ticketsystem.it_service_backend.dto.ClaimerDTO;
import com.ticketsystem.it_service_backend.dto.TicketAuditLogDTO;
import com.ticketsystem.it_service_backend.dto.TicketResponseDTO;
import com.ticketsystem.it_service_backend.entity.Product;
import com.ticketsystem.it_service_backend.entity.Ticket;
import com.ticketsystem.it_service_backend.entity.TicketAuditLog;
import com.ticketsystem.it_service_backend.entity.TicketClaim;
import com.ticketsystem.it_service_backend.repository.CsatRepository;
import com.ticketsystem.it_service_backend.repository.ProductRepository;
import com.ticketsystem.it_service_backend.repository.TicketAuditLogRepository;
import com.ticketsystem.it_service_backend.repository.TicketClaimRepository;
import com.ticketsystem.it_service_backend.util.AuthRoles;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Assembles {@link TicketResponseDTO}s and the internal "full ticket" bundle from a
 * {@link Ticket} entity plus its related records (claims, audit log, comments,
 * worklogs, known issues).
 *
 * <p>This is the single home for ticket presentation assembly. It exists so the web
 * layer ({@code TicketController}, {@code InternalTicketController}) does not reach
 * into repositories directly: foreign-key IDs (customer, product, claiming agents,
 * audit actors) are resolved to display names here, in one batched lookup per ticket
 * rather than a {@code findById} per row (avoids N+1). The role-aware variant
 * ({@link #toDto}) applies CSAT visibility rules; the internal variant
 * ({@link #toInternalDto}) exposes everything for service-to-service consumers.
 */
@Log4j2
@Service
@RequiredArgsConstructor
public class TicketDtoAssembler {

    private static final String UNKNOWN = "Unknown";
    private static final String CSAT_SUBMITTED = "CSAT_SUBMITTED";

    private final TicketService ticketService;
    private final UserService userService;
    private final CommentService commentService;
    private final WorklogService worklogService;
    private final KnownIssueService knownIssueService;
    private final ProductRepository productRepository;
    private final TicketClaimRepository ticketClaimRepository;
    private final TicketAuditLogRepository ticketAuditLogRepository;
    private final CsatRepository csatRepository;

    /**
     * Builds the role-aware ticket DTO for end-user endpoints. CSAT audit entries are
     * shown only to ADMIN/MANAGER and the owning customer; the CSAT rating is exposed
     * only to ADMIN/MANAGER.
     *
     * @param ticket the ticket to render
     * @param hasCsat whether the ticket has a CSAT record (carried into the DTO)
     * @param roles the caller's role set
     * @return the assembled DTO
     */
    @Transactional(readOnly = true)
    public TicketResponseDTO toDto(Ticket ticket, boolean hasCsat, List<String> roles) {
        // CSAT denetim kayıtları (puan/yorum içerir) yalnızca ADMIN/MANAGER ile biletin
        // sahibi müşteriye gösterilir; ajan/lead timeline'da görmez.
        boolean canSeeCsat = AuthRoles.isGlobal(roles) || roles.contains(AuthRoles.CUSTOMER);
        TicketResponseDTO dto = assemble(ticket, hasCsat, canSeeCsat);

        // CSAT puanı liste/detayda yalnızca ADMIN/MANAGER'a açılır (gizlilik sınırı).
        if (AuthRoles.isGlobal(roles)) {
            csatRepository.findByTicketId(ticket.getId())
                    .ifPresent(csat -> dto.setCsatRating(csat.getRating()));
        }
        return dto;
    }

    /**
     * Builds the ticket DTO for internal/service-to-service consumers (e.g. the LLM
     * service): the full audit timeline is included and no CSAT rating is exposed.
     *
     * @param ticket the ticket to render
     * @return the assembled DTO
     */
    @Transactional(readOnly = true)
    public TicketResponseDTO toInternalDto(Ticket ticket) {
        return assemble(ticket, false, true);
    }

    /**
     * Bundles all data for a ticket (ticket DTO, comments, worklogs, matching known
     * issues) into a single map for the internal full-ticket endpoint.
     *
     * @param ticketId identifier of the ticket
     * @return map with {@code ticket}, {@code comments}, {@code worklogs} and {@code knownIssues}
     */
    @Transactional(readOnly = true)
    public Map<String, Object> buildFullTicketData(Long ticketId) {
        Ticket ticket = ticketService.getTicketById(ticketId);
        return Map.of(
                "ticket", toInternalDto(ticket),
                "comments", commentService.getAllCommentDtos(ticketId),
                "worklogs", worklogService.getAllWorklogDtosByTicket(ticketId),
                "knownIssues", knownIssueService.getActiveForTicket(ticket.getProductId(), ticket.getTopicId())
        );
    }

    /**
     * Shared assembly: resolves customer/product/claimer/audit-actor names in one
     * batched lookup and builds the DTO. {@code includeCsatAudit} controls whether
     * {@code CSAT_SUBMITTED} audit entries are kept in the timeline.
     */
    private TicketResponseDTO assemble(Ticket ticket, boolean hasCsat, boolean includeCsatAudit) {
        List<TicketClaim> claims = ticketClaimRepository.findByTicketId(ticket.getId());
        List<TicketAuditLog> auditEntries =
                ticketAuditLogRepository.findByTicketIdOrderByCreatedAtDesc(ticket.getId());

        // Tüm kullanıcı kimliklerini tek sorguda çöz (customer + claim agent'ları +
        // audit actor'ları) — satır başına findById yerine tek findAllById (N+1 yok).
        Set<String> userIds = new HashSet<>();
        if (ticket.getCustomerId() != null) userIds.add(ticket.getCustomerId());
        claims.forEach(c -> userIds.add(c.getAgentId()));
        auditEntries.forEach(a -> userIds.add(a.getActorId()));
        Map<String, String> names = userService.getDisplayNames(userIds);

        String customerName = ticket.getCustomerId() != null
                ? names.getOrDefault(ticket.getCustomerId(), UNKNOWN) : UNKNOWN;
        // Ürün adları (tr/en) DTO'ya birlikte taşınır; dil seçimi istemcide yapılır.
        Product product = ticket.getProductId() != null
                ? productRepository.findById(ticket.getProductId()).orElse(null)
                : null;

        List<ClaimerDTO> claimers = claims.stream()
                .map(claim -> ClaimerDTO.builder()
                        .agentId(claim.getAgentId())
                        .agentName(claim.getAgentId() == null
                                ? UNKNOWN : names.getOrDefault(claim.getAgentId(), UNKNOWN))
                        .claimedAt(claim.getClaimedAt())
                        .build())
                .toList();

        // Actor adı bulunamazsa orijinal davranış gereği actorId'ye düşülür (UNKNOWN değil).
        List<TicketAuditLogDTO> auditLogs = auditEntries.stream()
                .filter(a -> includeCsatAudit || !CSAT_SUBMITTED.equals(a.getActionType()))
                .map(a -> TicketAuditLogDTO.fromEntity(a, names.getOrDefault(a.getActorId(), a.getActorId())))
                .toList();

        TicketResponseDTO dto = TicketResponseDTO.fromEntity(ticket, hasCsat, product, customerName, claimers);
        dto.setSlaInfo(ticketService.getSlaTimerInfo(ticket));
        dto.setAuditLogs(auditLogs);
        return dto;
    }
}
