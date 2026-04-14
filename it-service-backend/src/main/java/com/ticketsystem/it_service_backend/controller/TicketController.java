package com.ticketsystem.it_service_backend.controller;

import com.ticketsystem.it_service_backend.dto.TicketRequestDTO;
import com.ticketsystem.it_service_backend.dto.TicketResponseDTO;
import com.ticketsystem.it_service_backend.entity.Ticket;
import com.ticketsystem.it_service_backend.entity.User;
import com.ticketsystem.it_service_backend.entity.Product;
import com.ticketsystem.it_service_backend.repository.UserRepository;
import com.ticketsystem.it_service_backend.repository.ProductRepository;
import com.ticketsystem.it_service_backend.service.TicketService;
import com.ticketsystem.it_service_backend.util.JwtUtils;
import io.swagger.v3.oas.annotations.Operation;
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
@Tag(name = "Ticket Yönetimi", description = "Destek talepleri (Ticket) ile ilgili tüm işlemler")
@RestController
@RequestMapping("/api/tickets")
@RequiredArgsConstructor
public class TicketController {

    private final TicketService ticketService;
    private final UserRepository userRepository;
    private final ProductRepository productRepository;

    // Bilet yasam dongusu islemleri.

    // Musteri tarafindan yeni destek kaydi olusturur.
    @Operation(summary = "Yeni bilet oluştur", description = "Sadece Müşteri rolündeki kullanıcılar bilet açabilir.")
    @PostMapping
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<TicketResponseDTO> createTicket(@RequestBody TicketRequestDTO ticketRequest,
            @AuthenticationPrincipal Jwt jwt) {
        String customerId = jwt.getSubject();
        List<String> roles = JwtUtils.extractRoles(jwt);

        log.info("Yeni bilet oluşturma isteği. Müşteri ID: {}, Başlık: {}", customerId, ticketRequest.getTitle());
        log.debug("Bilet Detayları: {}", ticketRequest);

        Ticket ticket = Ticket.builder()
                .title(ticketRequest.getTitle())
                .description(ticketRequest.getDescription())
                .priority(ticketRequest.getPriority())
                .productId(ticketRequest.getProductId())
                .build();

        Ticket savedTicket = ticketService.createTicket(ticket, customerId);

        log.info("Bilet başarıyla oluşturuldu. Bilet ID: {}", savedTicket.getId());

        return ResponseEntity.ok(convertToDto(savedTicket, false, roles));
    }

    // Rol bilgisine gore kullanicinin gorebilecegi biletleri listeler.
    @Operation(summary = "Biletleri listele", description = "Müşteri kendi biletlerini, Ajan/Yönetici yetkili olduğu biletleri görür.")
    @GetMapping
    @PreAuthorize("hasAnyRole('CUSTOMER', 'AGENT', 'MANAGER')")
    public ResponseEntity<List<TicketResponseDTO>> getTickets(@AuthenticationPrincipal Jwt jwt) {
        String userId = jwt.getSubject();
        List<String> roles = JwtUtils.extractRoles(jwt);

        log.info("Biletleri listeleme isteği. Kullanıcı ID: {}, Roller: {}", userId, roles);

        List<Ticket> tickets;
        if (roles.contains("MANAGER") || roles.contains("AGENT")) {
            tickets = ticketService.getAllTickets(userId, roles);
        } else {
            tickets = ticketService.getCustomerTickets(userId);
        }

        log.info("Kullanıcı (ID: {}) için {} adet bilet listelendi.", userId, tickets.size());

        return ResponseEntity.ok(tickets.stream()
            .map(ticket -> convertToDto(ticket, false, roles))
                .collect(Collectors.toList()));
    }

    // Atanmamis havuz biletlerini agent/manager icin getirir.
    @Operation(summary = "Havuzdaki biletleri listele", description = "Henüz bir ajana atanmamış ve kullanıcının yetki dahilindeki biletler.")
    @GetMapping("/pool")
    @PreAuthorize("hasAnyRole('AGENT', 'MANAGER')")
    public ResponseEntity<List<TicketResponseDTO>> getPoolTickets(@AuthenticationPrincipal Jwt jwt) {
        String userId = jwt.getSubject();
        List<String> roles = JwtUtils.extractRoles(jwt);

        log.info("Bilet havuzunu listeleme isteği. Kullanıcı ID: {}", userId);

        List<Ticket> poolTickets = ticketService.getPoolTickets(userId, roles);

        log.info("Havuzda {} adet uygun bilet listelendi.", poolTickets.size());

        return ResponseEntity.ok(poolTickets.stream()
            .map(ticket -> convertToDto(ticket, false, roles))
                .collect(Collectors.toList()));
    }

    // Giris yapan agentin uzerindeki acik biletleri listeler.
    @Operation(summary = "Ajanın üzerindeki biletleri listele", description = "Giriş yapan ajana atanmış aktif biletler.")
    @GetMapping("/my-assigned")
    public ResponseEntity<List<TicketResponseDTO>> getMyAssignedTickets(@AuthenticationPrincipal Jwt jwt) {
        String agentId = jwt.getSubject();
        List<String> roles = JwtUtils.extractRoles(jwt);

        log.info("Ajan üzerindeki biletleri listeleme isteği. Ajan ID: {}", agentId);

        List<Ticket> tickets = ticketService.getAgentAssignedTickets(agentId);

        log.info("Ajan (ID: {}) üzerinde {} adet bilet bulundu.", agentId, tickets.size());

        return ResponseEntity.ok(tickets.stream()
            .map(ticket -> convertToDto(ticket, false, roles))
                .collect(Collectors.toList()));
    }

    // Tek bilet detayini yetki kontroluyle birlikte dondurur.
    @Operation(summary = "Bilet detayı getir", description = "ID ile bilet detayını çeker. Yetki kontrolü yapılır.")
    @GetMapping("/{id}")
    public ResponseEntity<TicketResponseDTO> getTicket(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        String userId = jwt.getSubject();
        List<String> roles = JwtUtils.extractRoles(jwt);

        log.info("Bilet detayı isteği. Bilet ID: {}, Kullanıcı: {}", id, userId);

        Ticket ticket = ticketService.getTicketWithAuth(id, userId, roles);
        log.info("Bilet detayı başarıyla çekildi. Bilet ID: {}", id);

        return ResponseEntity.ok(convertToDto(ticket, false, roles));
    }

    // Agentin havuzdaki bileti sahiplenmesini saglar.
    @Operation(summary = "Bileti sahiplen (Claim)", description = "Havuzdaki bir bileti ajanın kendi üzerine almasını sağlar.")
    @PutMapping("/{id}/claim")
    @PreAuthorize("hasRole('AGENT')")
    public ResponseEntity<TicketResponseDTO> claimTicket(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        String agentId = jwt.getSubject();

        log.info("Bileti sahiplenme (claim) isteği. Bilet ID: {}, Ajan ID: {}", id, agentId);

        Ticket ticket = ticketService.claimTicket(id, agentId);

        log.info("Bilet başarıyla sahiplenildi. Bilet ID: {}, Ajan ID: {}", id, agentId);

        return ResponseEntity.ok(convertToDto(ticket, false, List.of("AGENT")));
    }

    // Durum makinesi kurallarina uygun statu degisimi yapar.
    @Operation(summary = "Bilet statüsü güncelle", description = "Biletin durumunu (OPEN, IN_PROGRESS, RESOLVED, CLOSED) değiştirir.")
    @PutMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'AGENT', 'MANAGER')")
    public ResponseEntity<TicketResponseDTO> updateStatus(@PathVariable Long id, @RequestBody Map<String, String> body,
            @AuthenticationPrincipal Jwt jwt) {
        String newStatus = body.get("status");
        String userId = jwt.getSubject();
        List<String> roles = JwtUtils.extractRoles(jwt);

        log.info("Bilet statü güncelleme isteği. Bilet ID: {}, Yeni Statü: {}, Güncelleyen: {}", id, newStatus, userId);

        Ticket ticket = ticketService.updateTicketStatus(id, newStatus, userId, roles);

        log.info("Bilet statüsü başarıyla güncellendi. Bilet ID: {}, Yeni Statü: {}", id, ticket.getStatus());

        return ResponseEntity.ok(convertToDto(ticket, false, roles));
    }

    // Bileti ve bagli kayitlarini yonetici yetkisiyle siler.
    @Operation(summary = "Bileti sil", description = "Sadece Yönetici (MANAGER) bilet silebilir.")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<Void> deleteTicket(@PathVariable Long id) {
        log.info("Bilet silme isteği. Bilet ID: {}", id);

        ticketService.deleteTicket(id);

        log.info("Bilet başarıyla silindi. Bilet ID: {}", id);

        return ResponseEntity.noContent().build();
    }

    private TicketResponseDTO convertToDto(Ticket ticket) {
        return convertToDto(ticket, false, List.of());
    }

    private TicketResponseDTO convertToDto(Ticket ticket, boolean hasCsat, List<String> roles) {
        String customerName = ticket.getCustomerId() != null 
            ? userRepository.findById(ticket.getCustomerId()).map(User::getFullName).orElse("Unknown") 
            : "Unknown";
        String assigneeName = ticket.getAssigneeId() != null 
            ? userRepository.findById(ticket.getAssigneeId()).map(User::getFullName).orElse(null) 
            : null;
        String productName = ticket.getProductId() != null 
            ? productRepository.findById(ticket.getProductId()).map(Product::getName).orElse("Unknown") 
            : "Unknown";
        TicketResponseDTO dto = TicketResponseDTO.fromEntity(ticket, hasCsat, productName, customerName, assigneeName);
        if (roles.contains("AGENT") || roles.contains("MANAGER")) {
            dto.setSlaInfo(ticketService.getSlaTimerInfo(ticket));
        }
        return dto;
    }

    @GetMapping("/{id}/sla-timer")
    @Operation(summary = "Get SLA timer information from jBPM", description = "Returns the precise Unix timestamp (ms) for the SLA deadline, or remaining milliseconds if paused.")
    @PreAuthorize("hasAnyRole('AGENT', 'MANAGER')")
    public ResponseEntity<Map<String, Long>> getSlaTimer(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        String userId = jwt.getSubject();
        List<String> roles = JwtUtils.extractRoles(jwt);
        Ticket ticket = ticketService.getTicketWithAuth(id, userId, roles);
        Map<String, Long> response = ticketService.getSlaTimerInfo(ticket);
        return ResponseEntity.ok(response);
    }

}