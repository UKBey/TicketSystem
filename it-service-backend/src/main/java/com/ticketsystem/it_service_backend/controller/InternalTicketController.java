package com.ticketsystem.it_service_backend.controller;

import com.ticketsystem.it_service_backend.dto.CommentDTO;
import com.ticketsystem.it_service_backend.dto.TicketAuditLogDTO;
import com.ticketsystem.it_service_backend.dto.TicketResponseDTO;
import com.ticketsystem.it_service_backend.dto.WorklogResponseDTO;
import com.ticketsystem.it_service_backend.entity.ResolutionNote;
import com.ticketsystem.it_service_backend.entity.Ticket;
import com.ticketsystem.it_service_backend.entity.User;
import com.ticketsystem.it_service_backend.dto.ClaimerDTO;
import com.ticketsystem.it_service_backend.repository.CommentRepository;
import com.ticketsystem.it_service_backend.repository.ProductRepository;
import com.ticketsystem.it_service_backend.repository.ResolutionNoteRepository;
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
 * Servisler arası (internal) iletişim için ticket verisi endpoint'leri.
 * Bu endpoint'ler yalnızca X-Internal-Token header'ı ile erişilebilir
 * (SecurityConfig'de tanımlı).
 */
@Log4j2
@Tag(name = "Internal", description = "Servisler arası iletişim endpoint'leri (JWT gerektirmez, internal token gerektirir)")
@RestController
@RequestMapping("/api/internal/tickets")
@RequiredArgsConstructor
public class InternalTicketController {

    private final TicketService ticketService;
    private final CommentRepository commentRepository;
    private final WorklogRepository worklogRepository;
    private final ResolutionNoteRepository resolutionNoteRepository;
    private final TicketAuditLogRepository ticketAuditLogRepository;
    private final TicketClaimRepository ticketClaimRepository;
    private final UserRepository userRepository;
    private final ProductRepository productRepository;

    /**
     * LLM servisi için bir ticket'ın tüm verisini tek seferde döner.
     * Yorumlar, worklog'lar, çözüm notu ve audit log dahildir.
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
                        .map(TicketAuditLogDTO::fromEntity)
                        .collect(Collectors.toList())
        );

        List<CommentDTO> comments = commentRepository.findByTicketIdOrderByCreatedAtAsc(ticketId).stream()
                .map(c -> CommentDTO.fromEntity(c,
                        userRepository.findById(c.getAuthorId()).map(User::getFullName).orElse("Unknown")))
                .collect(Collectors.toList());

        List<WorklogResponseDTO> worklogs = worklogRepository.findByTicketId(ticketId).stream()
                .map(WorklogResponseDTO::fromEntity)
                .collect(Collectors.toList());

        ResolutionNote resolutionNote = resolutionNoteRepository.findByTicketId(ticketId).orElse(null);

        Map<String, Object> response = Map.of(
                "ticket", ticketDTO,
                "comments", comments,
                "worklogs", worklogs,
                "resolutionNote", resolutionNote != null
                        ? Map.of("note", resolutionNote.getNote(), "createdAt", resolutionNote.getCreatedAt())
                        : Map.of()
        );

        return ResponseEntity.ok(response);
    }
}
