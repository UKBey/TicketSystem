package com.ticketsystem.it_service_backend.controller;

import com.ticketsystem.it_service_backend.dto.CommentDTO;
import com.ticketsystem.it_service_backend.dto.KnownIssueDTO;
import com.ticketsystem.it_service_backend.dto.TicketAuditLogDTO;
import com.ticketsystem.it_service_backend.dto.TicketResponseDTO;
import com.ticketsystem.it_service_backend.dto.WorklogResponseDTO;
import com.ticketsystem.it_service_backend.entity.Ticket;
import com.ticketsystem.it_service_backend.entity.User;
import com.ticketsystem.it_service_backend.dto.ClaimerDTO;
import com.ticketsystem.it_service_backend.repository.CommentRepository;
import com.ticketsystem.it_service_backend.repository.KnownIssueRepository;
import com.ticketsystem.it_service_backend.repository.ProductRepository;
import com.ticketsystem.it_service_backend.repository.TicketAuditLogRepository;
import com.ticketsystem.it_service_backend.repository.TicketClaimRepository;
import com.ticketsystem.it_service_backend.repository.UserRepository;
import com.ticketsystem.it_service_backend.repository.WorklogRepository;
import com.ticketsystem.it_service_backend.service.TicketService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Ticket data endpoints for service-to-service (internal) communication.
 *
 * <p>These endpoints are only reachable with the {@code X-Internal-Token} header
 * (configured in SecurityConfig; no JWT is required). The primary consumer is the
 * LLM service, which fetches all the context for a ticket in a single call; business
 * logic is delegated to {@link TicketService} and the relevant repositories.
 */
@Log4j2
@Tag(name = "Internal", description = "Servisler arası iletişim endpoint'leri (JWT gerektirmez, internal token gerektirir)")
@RestController
@RequestMapping("/api/v1/internal/tickets")
@RequiredArgsConstructor
public class InternalTicketController {

    private final TicketService ticketService;
    private final CommentRepository commentRepository;
    private final WorklogRepository worklogRepository;
    private final TicketAuditLogRepository ticketAuditLogRepository;
    private final TicketClaimRepository ticketClaimRepository;
    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final KnownIssueRepository knownIssueRepository;

    /**
     * Returns all data for a ticket in a single call, intended for the LLM service.
     *
     * <p>Comments, worklogs, audit log, list of claiming agents, SLA information and
     * matching known-issue records are bundled into the same response.
     *
     * @param ticketId identifier of the ticket whose data is requested
     * @return map containing the {@code ticket}, {@code comments}, {@code worklogs} and {@code knownIssues} keys
     */
    @Operation(summary = "Ticket'ın tüm verisini getir (internal)")
    @GetMapping("/{ticketId}/full")
    public ResponseEntity<Map<String, Object>> getFullTicketData(@PathVariable Long ticketId) {
        log.info("Internal full ticket data isteği. TicketId: {}", ticketId);

        Ticket ticket = ticketService.getTicketById(ticketId);

        String customerName = userRepository.findById(ticket.getCustomerId())
                .map(User::getFullName).orElse("Unknown");
        String productName = ticket.getProductId() != null
                ? productRepository.findById(ticket.getProductId()).map(p -> p.getName()).orElse("Unknown")
                : "Unknown";

        List<ClaimerDTO> claimers = ticketClaimRepository.findByTicketId(ticketId).stream()
                .map(claim -> ClaimerDTO.builder()
                        .agentId(claim.getAgentId())
                        .agentName(userRepository.findById(claim.getAgentId())
                                .map(User::getFullName).orElse("Unknown"))
                        .claimedAt(claim.getClaimedAt())
                        .build())
                .collect(Collectors.toList());

        TicketResponseDTO ticketDTO = TicketResponseDTO.fromEntity(ticket, false, productName, customerName, claimers);
        ticketDTO.setSlaInfo(ticketService.getSlaTimerInfo(ticket));
        ticketDTO.setAuditLogs(
                ticketAuditLogRepository.findByTicketIdOrderByCreatedAtDesc(ticketId).stream()
                        .map(log -> TicketAuditLogDTO.fromEntity(log,
                                userRepository.findById(log.getActorId())
                                        .map(User::getFullName)
                                        .orElse(log.getActorId())))
                        .collect(Collectors.toList())
        );

        List<CommentDTO> comments = commentRepository.findByTicketIdOrderByCreatedAtAsc(ticketId).stream()
                .map(c -> {
                    User author = c.getAuthorId() != null
                            ? userRepository.findById(c.getAuthorId()).orElse(null)
                            : null;
                    String authorName = author != null ? author.getFullName() : "Unknown";
                    String authorRole = author != null ? author.getRole() : null;
                    return CommentDTO.fromEntity(c, authorName, authorRole);
                })
                .collect(Collectors.toList());

        List<WorklogResponseDTO> worklogs = worklogRepository.findByTicketId(ticketId).stream()
                .map(WorklogResponseDTO::fromEntity)
                .collect(Collectors.toList());

        // LLM özetinin "bilinen sorun eşleşmesi" yapabilmesi için bilet konusuna
        // ilişkin kayıtları da gönderiyoruz: bilete özel topic kayıtları + ürün
        // genelindeki (topic'siz) kayıtlar. Yalnızca aktif olanlar dahil edilir.
        List<KnownIssueDTO> knownIssues = ticket.getProductId() != null
                ? knownIssueRepository
                        .findByProductIdAndIsActiveTrueOrderByCreatedAtDesc(ticket.getProductId()).stream()
                        .filter(ki -> ki.getTopicId() == null
                                || ki.getTopicId().equals(ticket.getTopicId()))
                        .map(KnownIssueDTO::fromEntity)
                        .collect(Collectors.toList())
                : List.of();

        Map<String, Object> response = Map.of(
                "ticket", ticketDTO,
                "comments", comments,
                "worklogs", worklogs,
                "knownIssues", knownIssues
        );

        return ResponseEntity.ok(response);
    }
}
