package com.ticketsystem.it_service_backend.controller;

import com.ticketsystem.it_service_backend.dto.ClaimerDTO;
import com.ticketsystem.it_service_backend.dto.TicketRequestDTO;
import com.ticketsystem.it_service_backend.dto.TicketResponseDTO;
import com.ticketsystem.it_service_backend.entity.Product;
import com.ticketsystem.it_service_backend.entity.Ticket;
import com.ticketsystem.it_service_backend.entity.User;
import com.ticketsystem.it_service_backend.repository.TicketClaimRepository;
import com.ticketsystem.it_service_backend.repository.UserRepository;
import com.ticketsystem.it_service_backend.repository.ProductRepository;
import com.ticketsystem.it_service_backend.service.TicketService;
import com.ticketsystem.it_service_backend.util.JwtUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Log4j2
@Tag(name = "Bilet Yönetimi", description = "Destek biletlerinin oluşturulması, listelenmesi, sahiplenilmesi ve yönetimi")
@RestController
@RequestMapping("/api/tickets")
@RequiredArgsConstructor
public class TicketController {

    private final TicketService ticketService;
    private final TicketClaimRepository ticketClaimRepository;
    private final UserRepository userRepository;
    private final ProductRepository productRepository;

    @Operation(summary = "Yeni bilet oluştur")
    @PostMapping
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<TicketResponseDTO> createTicket(@RequestBody TicketRequestDTO dto,
            @AuthenticationPrincipal Jwt jwt) {
        String customerId = jwt.getSubject();
        List<String> roles = JwtUtils.extractRoles(jwt);

        Ticket ticket = Ticket.builder()
                .title(dto.getTitle())
                .description(dto.getDescription())
                .priority(dto.getPriority())
                .productId(dto.getProductId())
                .build();

        Ticket saved = ticketService.createTicket(ticket, customerId);
        return ResponseEntity.ok(convertToDto(saved, false, roles));
    }

    @Operation(summary = "Biletleri listele (role göre filtrelenir)")
    @GetMapping
    @PreAuthorize("hasAnyRole('CUSTOMER', 'AGENT', 'AGENT_ADMIN')")
    public ResponseEntity<List<TicketResponseDTO>> getTickets(@AuthenticationPrincipal Jwt jwt) {
        String userId = jwt.getSubject();
        List<String> roles = JwtUtils.extractRoles(jwt);

        List<Ticket> tickets = (roles.contains("AGENT_ADMIN") || roles.contains("AGENT"))
                ? ticketService.getAllTickets(userId, roles)
                : ticketService.getCustomerTickets(userId);

        return ResponseEntity.ok(tickets.stream()
                .map(t -> convertToDto(t, false, roles))
                .collect(Collectors.toList()));
    }

    @Operation(summary = "Havuzdaki biletleri listele (NEW statüsü, ürüne göre filtrelenir)")
    @GetMapping("/pool")
    @PreAuthorize("hasAnyRole('AGENT', 'AGENT_ADMIN')")
    public ResponseEntity<List<TicketResponseDTO>> getPoolTickets(@AuthenticationPrincipal Jwt jwt) {
        String userId = jwt.getSubject();
        List<String> roles = JwtUtils.extractRoles(jwt);
        List<Ticket> pool = ticketService.getPoolTickets(userId, roles);
        return ResponseEntity.ok(pool.stream()
                .map(t -> convertToDto(t, false, roles))
                .collect(Collectors.toList()));
    }

    @Operation(summary = "Ajanın claim aldığı biletleri listele")
    @GetMapping("/my-assigned")
    public ResponseEntity<List<TicketResponseDTO>> getMyAssignedTickets(@AuthenticationPrincipal Jwt jwt) {
        String agentId = jwt.getSubject();
        List<String> roles = JwtUtils.extractRoles(jwt);
        List<Ticket> tickets = ticketService.getAgentClaimedTickets(agentId);
        return ResponseEntity.ok(tickets.stream()
                .map(t -> convertToDto(t, false, roles))
                .collect(Collectors.toList()));
    }

    @Operation(summary = "Ajanın yetkili ürünlerindeki aktif biletleri listele (Team Tickets paneli)")
    @GetMapping("/team")
    @PreAuthorize("hasAnyRole('AGENT', 'AGENT_ADMIN')")
    public ResponseEntity<List<TicketResponseDTO>> getTeamTickets(@AuthenticationPrincipal Jwt jwt) {
        String userId = jwt.getSubject();
        List<String> roles = JwtUtils.extractRoles(jwt);
        List<Ticket> tickets = ticketService.getTeamTickets(userId, roles);
        return ResponseEntity.ok(tickets.stream()
                .map(t -> convertToDto(t, false, roles))
                .collect(Collectors.toList()));
    }

    @Operation(summary = "Bilet detayı getir")
    @GetMapping("/{id}")
    public ResponseEntity<TicketResponseDTO> getTicket(
            @PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        String userId = jwt.getSubject();
        List<String> roles = JwtUtils.extractRoles(jwt);
        Ticket ticket = ticketService.getTicketWithAuth(id, userId, roles);
        return ResponseEntity.ok(convertToDto(ticket, false, roles));
    }

    @Operation(summary = "Bileti claim al (NEW veya IN_PROGRESS statüsündeki bileti sahiplen)")
    @PutMapping("/{id}/claim")
    @PreAuthorize("hasAnyRole('AGENT', 'AGENT_ADMIN')")
    public ResponseEntity<TicketResponseDTO> claimTicket(
            @PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        String agentId = jwt.getSubject();
        List<String> roles = JwtUtils.extractRoles(jwt);
        Ticket ticket = ticketService.claimTicket(id, agentId);
        return ResponseEntity.ok(convertToDto(ticket, false, roles));
    }

    @Operation(summary = "Claim'i bırak (sadece kendi claim'ini geri verir)")
    @DeleteMapping("/{id}/claim")
    @PreAuthorize("hasAnyRole('AGENT', 'AGENT_ADMIN')")
    public ResponseEntity<TicketResponseDTO> unclaimTicket(
            @PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        String agentId = jwt.getSubject();
        List<String> roles = JwtUtils.extractRoles(jwt);
        Ticket ticket = ticketService.unclaimTicket(id, agentId);
        return ResponseEntity.ok(convertToDto(ticket, false, roles));
    }

    @Operation(summary = "Bilet statüsü güncelle")
    @PutMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'AGENT', 'AGENT_ADMIN')")
    public ResponseEntity<TicketResponseDTO> updateStatus(
            @PathVariable Long id,
            @RequestBody Map<String, String> body,
            @AuthenticationPrincipal Jwt jwt) {
        String newStatus = body.get("status");
        String userId = jwt.getSubject();
        List<String> roles = JwtUtils.extractRoles(jwt);
        Ticket ticket = ticketService.updateTicketStatus(id, newStatus, userId, roles);
        return ResponseEntity.ok(convertToDto(ticket, false, roles));
    }

    @Operation(summary = "Bileti sil (AGENT_ADMIN yetkisi gerekir)")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('AGENT_ADMIN')")
    public ResponseEntity<Void> deleteTicket(
            @Parameter(description = "Silinecek biletin ID'si") @PathVariable Long id) {
        ticketService.deleteTicket(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "SLA zamanlayıcı bilgisi")
    @GetMapping("/{id}/sla-timer")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'AGENT', 'AGENT_ADMIN')")
    public ResponseEntity<Map<String, Long>> getSlaTimer(
            @PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        String userId = jwt.getSubject();
        List<String> roles = JwtUtils.extractRoles(jwt);
        Ticket ticket = ticketService.getTicketWithAuth(id, userId, roles);
        return ResponseEntity.ok(ticketService.getSlaTimerInfo(ticket));
    }

    // -----------------------------------------------------------------
    // DTO dönüşümü
    // -----------------------------------------------------------------

    private TicketResponseDTO convertToDto(Ticket ticket, boolean hasCsat, List<String> roles) {
        String customerName = ticket.getCustomerId() != null
                ? userRepository.findById(ticket.getCustomerId()).map(User::getFullName).orElse("Unknown")
                : "Unknown";
        String productName = ticket.getProductId() != null
                ? productRepository.findById(ticket.getProductId()).map(Product::getName).orElse("Unknown")
                : "Unknown";

        List<ClaimerDTO> claimers = ticketClaimRepository.findByTicketId(ticket.getId()).stream()
                .map(claim -> ClaimerDTO.builder()
                        .agentId(claim.getAgentId())
                        .agentName(userRepository.findById(claim.getAgentId())
                                .map(User::getFullName).orElse("Unknown"))
                        .claimedAt(claim.getClaimedAt())
                        .build())
                .collect(Collectors.toList());

        TicketResponseDTO dto = TicketResponseDTO.fromEntity(ticket, hasCsat, productName, customerName, claimers);
        dto.setSlaInfo(ticketService.getSlaTimerInfo(ticket));
        return dto;
    }
}
