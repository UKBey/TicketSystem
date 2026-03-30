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
import com.ticketsystem.it_service_backend.util.JwtUtils;

@RestController
@RequestMapping("/api/tickets")
@RequiredArgsConstructor
public class TicketController {

    private final TicketService ticketService;

    // 1. Yeni Bilet Oluştur (Müşteri)
    @PostMapping
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<Ticket> createTicket(@RequestBody Ticket ticket, @AuthenticationPrincipal Jwt jwt) {
        String customerId = jwt.getSubject(); // Keycloak Token'ından ID'yi alır
        return ResponseEntity.ok(ticketService.createTicket(ticket, customerId));
    }

    // 2. Biletleri Listele (Gelişmiş - Rol bazlı geri dönüş)
    @GetMapping
    @PreAuthorize("hasAnyRole('CUSTOMER', 'AGENT', 'MANAGER')")
    public ResponseEntity<List<Ticket>> getTickets(@AuthenticationPrincipal Jwt jwt) {
        String userId = jwt.getSubject(); // Keycloak Kullanıcı ID'si

        // 1. Keycloak Token'ı içindeki roller
        List<String> roles = JwtUtils.extractRoles(jwt);

        // 2. Kullanıcının rolüne göre hangi verinin döneceğine karar veriyoruz
        if (roles.contains("MANAGER") || roles.contains("AGENT")) {
            // Kullanıcı yetkili biriyse (Uzman veya Yönetici), yetkisine uyan biletleri
            // döndür
            return ResponseEntity.ok(ticketService.getAllTickets(userId, roles));
        } else {
            // Kullanıcı sıradan bir CUSTOMER ise, SADECE kendi ID'siyle eşleşen biletleri
            // döndür
            return ResponseEntity.ok(ticketService.getCustomerTickets(userId));
        }
    }

    // 3. Havuzdaki Atanmamış Biletleri Getir (Ajan/Yönetici)
    @GetMapping("/pool")
    @PreAuthorize("hasAnyRole('AGENT', 'MANAGER')")
    public ResponseEntity<List<Ticket>> getPoolTickets(@AuthenticationPrincipal Jwt jwt) {
        String userId = jwt.getSubject();
        List<String> roles = JwtUtils.extractRoles(jwt);
        return ResponseEntity.ok(ticketService.getPoolTickets(userId, roles));
    }

    // 4. Ajanın Kendi Üzerindeki Biletleri Getir
    @GetMapping("/my-assigned")
    public ResponseEntity<List<Ticket>> getMyAssignedTickets(@AuthenticationPrincipal Jwt jwt) {
        String agentId = jwt.getSubject();
        return ResponseEntity.ok(ticketService.getAgentAssignedTickets(agentId));
    }

    // 5. Tekil Bilet Detayı (Güvenli)
    @GetMapping("/{id}")
    public ResponseEntity<Ticket> getTicket(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        String userId = jwt.getSubject();
        List<String> roles = JwtUtils.extractRoles(jwt);
        return ResponseEntity.ok(ticketService.getTicketWithAuth(id, userId, roles));
    }

    // 6. Havuzdan Bilet Üzerine Alma (Claim)
    @PutMapping("/{id}/claim")
    @PreAuthorize("hasRole('AGENT')")
    public ResponseEntity<Ticket> claimTicket(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        String agentId = jwt.getSubject();
        return ResponseEntity.ok(ticketService.claimTicket(id, agentId));
    }

    // 7. Statü Güncelleme
    @PutMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('AGENT', 'MANAGER')")
    public ResponseEntity<Ticket> updateStatus(@PathVariable Long id, @RequestBody Map<String, String> body, @AuthenticationPrincipal Jwt jwt) {
        String newStatus = body.get("status");
        String userId = jwt.getSubject();
        List<String> roles = JwtUtils.extractRoles(jwt);
        return ResponseEntity.ok(ticketService.updateTicketStatus(id, newStatus, userId, roles));
    }

    // 8. Bilet Silme
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<Void> deleteTicket(@PathVariable Long id) {
        ticketService.deleteTicket(id);
        return ResponseEntity.noContent().build();
    }
}