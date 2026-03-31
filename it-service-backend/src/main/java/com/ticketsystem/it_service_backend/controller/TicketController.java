package com.ticketsystem.it_service_backend.controller;

import com.ticketsystem.it_service_backend.entity.Ticket;
import com.ticketsystem.it_service_backend.service.TicketService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import com.ticketsystem.it_service_backend.dto.TicketRequestDTO;
import com.ticketsystem.it_service_backend.dto.TicketResponseDTO;
import com.ticketsystem.it_service_backend.util.JwtUtils;

@RestController
@RequestMapping("/api/tickets")
@RequiredArgsConstructor
public class TicketController {

    private final TicketService ticketService;

    // 1. Yeni Bilet Oluştur (Müşteri)
    @PostMapping
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<TicketResponseDTO> createTicket(@RequestBody TicketRequestDTO ticketRequest, @AuthenticationPrincipal Jwt jwt) {
        String customerId = jwt.getSubject(); // Keycloak Token'ından ID'yi alır
        
        Ticket ticket = Ticket.builder()
                .title(ticketRequest.getTitle())
                .description(ticketRequest.getDescription())
                .priority(ticketRequest.getPriority())
                .productId(ticketRequest.getProductId())
                .build();

        Ticket savedTicket = ticketService.createTicket(ticket, customerId);
        return ResponseEntity.ok(TicketResponseDTO.fromEntity(savedTicket));
    }

    // 2. Biletleri Listele (Gelişmiş - Rol bazlı geri dönüş)
    @GetMapping
    @PreAuthorize("hasAnyRole('CUSTOMER', 'AGENT', 'MANAGER')")
    public ResponseEntity<List<TicketResponseDTO>> getTickets(@AuthenticationPrincipal Jwt jwt) {
        String userId = jwt.getSubject(); // Keycloak Kullanıcı ID'si

        // 1. Keycloak Token'ı içindeki roller
        List<String> roles = JwtUtils.extractRoles(jwt);

        List<Ticket> tickets;
        // 2. Kullanıcının rolüne göre hangi verinin döneceğine karar veriyoruz
        if (roles.contains("MANAGER") || roles.contains("AGENT")) {
            tickets = ticketService.getAllTickets(userId, roles);
        } else {
            tickets = ticketService.getCustomerTickets(userId);
        }

        return ResponseEntity.ok(tickets.stream()
                .map(TicketResponseDTO::fromEntity)
                .collect(Collectors.toList()));
    }

    // 3. Havuzdaki Atanmamış Biletleri Getir (Ajan/Yönetici)
    @GetMapping("/pool")
    @PreAuthorize("hasAnyRole('AGENT', 'MANAGER')")
    public ResponseEntity<List<TicketResponseDTO>> getPoolTickets(@AuthenticationPrincipal Jwt jwt) {
        String userId = jwt.getSubject();
        List<String> roles = JwtUtils.extractRoles(jwt);
        return ResponseEntity.ok(ticketService.getPoolTickets(userId, roles).stream()
                .map(TicketResponseDTO::fromEntity)
                .collect(Collectors.toList()));
    }

    // 4. Ajanın Kendi Üzerindeki Biletleri Getir
    @GetMapping("/my-assigned")
    public ResponseEntity<List<TicketResponseDTO>> getMyAssignedTickets(@AuthenticationPrincipal Jwt jwt) {
        String agentId = jwt.getSubject();
        return ResponseEntity.ok(ticketService.getAgentAssignedTickets(agentId).stream()
                .map(TicketResponseDTO::fromEntity)
                .collect(Collectors.toList()));
    }

    // 5. Tekil Bilet Detayı (Güvenli)
    @GetMapping("/{id}")
    public ResponseEntity<TicketResponseDTO> getTicket(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        String userId = jwt.getSubject();
        List<String> roles = JwtUtils.extractRoles(jwt);
        return ResponseEntity.ok(TicketResponseDTO.fromEntity(ticketService.getTicketWithAuth(id, userId, roles)));
    }

    // 6. Havuzdan Bilet Üzerine Alma (Claim)
    @PutMapping("/{id}/claim")
    @PreAuthorize("hasRole('AGENT')")
    public ResponseEntity<TicketResponseDTO> claimTicket(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        String agentId = jwt.getSubject();
        return ResponseEntity.ok(TicketResponseDTO.fromEntity(ticketService.claimTicket(id, agentId)));
    }

    // 7. Statü Güncelleme
    @PutMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('AGENT', 'MANAGER')")
    public ResponseEntity<TicketResponseDTO> updateStatus(@PathVariable Long id, @RequestBody Map<String, String> body, @AuthenticationPrincipal Jwt jwt) {
        String newStatus = body.get("status");
        String userId = jwt.getSubject();
        List<String> roles = JwtUtils.extractRoles(jwt);
        return ResponseEntity.ok(TicketResponseDTO.fromEntity(ticketService.updateTicketStatus(id, newStatus, userId, roles)));
    }

    // 8. Bilet Silme
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<Void> deleteTicket(@PathVariable Long id) {
        ticketService.deleteTicket(id);
        return ResponseEntity.noContent().build();
    }
}