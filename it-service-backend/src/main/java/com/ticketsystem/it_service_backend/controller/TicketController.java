package com.ticketsystem.it_service_backend.controller;

import com.ticketsystem.it_service_backend.dto.AssignTicketRequestDTO;
import com.ticketsystem.it_service_backend.dto.ClaimerDTO;
import com.ticketsystem.it_service_backend.dto.CloseTicketRequestDTO;
import com.ticketsystem.it_service_backend.dto.TicketFilterDTO;
import com.ticketsystem.it_service_backend.dto.TicketRequestDTO;
import com.ticketsystem.it_service_backend.dto.TicketResponseDTO;
import com.ticketsystem.it_service_backend.dto.TicketAuditLogDTO;
import com.ticketsystem.it_service_backend.dto.UnclaimRequestDTO;
import com.ticketsystem.it_service_backend.entity.Product;
import com.ticketsystem.it_service_backend.entity.Ticket;
import com.ticketsystem.it_service_backend.entity.User;
import com.ticketsystem.it_service_backend.repository.TicketClaimRepository;
import com.ticketsystem.it_service_backend.repository.TicketAuditLogRepository;
import com.ticketsystem.it_service_backend.repository.UserRepository;
import com.ticketsystem.it_service_backend.repository.ProductRepository;
import com.ticketsystem.it_service_backend.service.TicketService;
import com.ticketsystem.it_service_backend.util.JwtUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.time.ZonedDateTime;
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
    private final TicketAuditLogRepository ticketAuditLogRepository;
    private final UserRepository userRepository;
    private final ProductRepository productRepository;

    @Operation(summary = "Yeni bilet oluştur")
    @PostMapping
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<TicketResponseDTO> createTicket(@RequestBody TicketRequestDTO dto,
            @AuthenticationPrincipal Jwt jwt) {
        String customerId = jwt.getSubject();
        List<String> roles = JwtUtils.extractRoles(jwt);

        log.info("Yeni bilet oluşturma isteği. Müşteri: {}, Ürün: {}, Öncelik: {}",
                customerId, dto.getProductId(), dto.getPriority());

        Ticket ticket = Ticket.builder()
                .title(dto.getTitle())
                .description(dto.getDescription())
                .priority(dto.getPriority())
                .productId(dto.getProductId())
                .build();

        Ticket saved = ticketService.createTicket(ticket, customerId);
        log.info("Bilet başarıyla oluşturuldu. Bilet ID: {}", saved.getId());
        return ResponseEntity.ok(convertToDto(saved, false, roles));
    }

    @Operation(summary = "Biletleri listele — sayfalama + filtreleme (role göre filtrelenir)")
    @GetMapping
    @PreAuthorize("hasAnyRole('CUSTOMER', 'AGENT', 'AGENT_ADMIN')")
    public ResponseEntity<Page<TicketResponseDTO>> getTickets(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0")   int page,
            @RequestParam(defaultValue = "20")  int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir,
            @RequestParam(required = false) List<String> status,
            @RequestParam(required = false) List<String> priority,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) List<Long> productId,
            @RequestParam(required = false) String agentId,
            @RequestParam(required = false) List<String> slaStatus,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) ZonedDateTime dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) ZonedDateTime dateTo) {

        String userId = jwt.getSubject();
        List<String> roles = JwtUtils.extractRoles(jwt);
        Sort sort = sortDir.equalsIgnoreCase("asc") ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
        PageRequest pageable = PageRequest.of(page, size, sort);
        TicketFilterDTO filter = TicketFilterDTO.builder()
                .statuses(status).priorities(priority).search(search)
                .productIds(productId).agentId(agentId).slaStatuses(slaStatus)
                .createdAtFrom(dateFrom).createdAtTo(dateTo).build();

        Page<Ticket> tickets;
        if (roles.contains("AGENT_ADMIN") || roles.contains("AGENT")) {
            tickets = ticketService.getTeamTicketsFiltered(userId, roles, filter, pageable);
        } else {
            tickets = ticketService.getCustomerTicketsFiltered(userId, filter, pageable);
        }
        return ResponseEntity.ok(tickets.map(t -> convertToDto(t, false, roles)));
    }

    @Operation(summary = "Havuzdaki biletleri listele — sayfalama + filtreleme (NEW statüsü)")
    @GetMapping("/pool")
    @PreAuthorize("hasAnyRole('AGENT', 'AGENT_ADMIN')")
    public ResponseEntity<Page<TicketResponseDTO>> getPoolTickets(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0")   int page,
            @RequestParam(defaultValue = "20")  int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir,
            @RequestParam(required = false) List<String> priority,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) List<Long> productId,
            @RequestParam(required = false) String agentId,
            @RequestParam(required = false) List<String> slaStatus,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) ZonedDateTime dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) ZonedDateTime dateTo) {

        String userId = jwt.getSubject();
        List<String> roles = JwtUtils.extractRoles(jwt);
        Sort sort = sortDir.equalsIgnoreCase("asc") ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
        PageRequest pageable = PageRequest.of(page, size, sort);
        TicketFilterDTO filter = TicketFilterDTO.builder()
                .priorities(priority).search(search).productIds(productId)
                .agentId(agentId).slaStatuses(slaStatus)
                .createdAtFrom(dateFrom).createdAtTo(dateTo).build();

        Page<Ticket> pool = ticketService.getPoolTicketsFiltered(userId, roles, filter, pageable);
        return ResponseEntity.ok(pool.map(t -> convertToDto(t, false, roles)));
    }

    @Operation(summary = "Ajanın claim aldığı biletleri listele — sayfalama + filtreleme")
    @GetMapping("/my-assigned")
    public ResponseEntity<Page<TicketResponseDTO>> getMyAssignedTickets(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0")   int page,
            @RequestParam(defaultValue = "20")  int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir,
            @RequestParam(required = false) List<String> status,
            @RequestParam(required = false) List<String> priority,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) List<Long> productId,
            @RequestParam(required = false) String agentId,
            @RequestParam(required = false) List<String> slaStatus,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) ZonedDateTime dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) ZonedDateTime dateTo) {

        String agentUserId = jwt.getSubject();
        List<String> roles = JwtUtils.extractRoles(jwt);
        Sort sort = sortDir.equalsIgnoreCase("asc") ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
        PageRequest pageable = PageRequest.of(page, size, sort);
        TicketFilterDTO filter = TicketFilterDTO.builder()
                .statuses(status).priorities(priority).search(search)
                .productIds(productId).agentId(agentId).slaStatuses(slaStatus)
                .createdAtFrom(dateFrom).createdAtTo(dateTo).build();

        Page<Ticket> tickets = ticketService.getAgentClaimedTicketsFiltered(agentUserId, filter, pageable);
        return ResponseEntity.ok(tickets.map(t -> convertToDto(t, false, roles)));
    }

    @Operation(summary = "Ajanın yetkili ürünlerindeki aktif biletleri listele — sayfalama + filtreleme")
    @GetMapping("/team")
    @PreAuthorize("hasAnyRole('AGENT', 'AGENT_ADMIN')")
    public ResponseEntity<Page<TicketResponseDTO>> getTeamTickets(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0")   int page,
            @RequestParam(defaultValue = "20")  int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir,
            @RequestParam(required = false) List<String> priority,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) List<Long> productId,
            @RequestParam(required = false) String agentId,
            @RequestParam(required = false) List<String> slaStatus,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) ZonedDateTime dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) ZonedDateTime dateTo) {

        String userId = jwt.getSubject();
        List<String> roles = JwtUtils.extractRoles(jwt);
        Sort sort = sortDir.equalsIgnoreCase("asc") ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
        PageRequest pageable = PageRequest.of(page, size, sort);
        TicketFilterDTO filter = TicketFilterDTO.builder()
                .priorities(priority).search(search).productIds(productId)
                .agentId(agentId).slaStatuses(slaStatus)
                .createdAtFrom(dateFrom).createdAtTo(dateTo).build();

        Page<Ticket> tickets = ticketService.getTeamTicketsFiltered(userId, roles, filter, pageable);
        return ResponseEntity.ok(tickets.map(t -> convertToDto(t, false, roles)));
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
        log.info("Bilet claim isteği. Bilet ID: {}, Agent: {}", id, agentId);
        Ticket ticket = ticketService.claimTicket(id, agentId);
        log.info("Bilet başarıyla claim alındı. Bilet ID: {}", id);
        return ResponseEntity.ok(convertToDto(ticket, false, roles));
    }

    @Operation(summary = "Claim'i bırak (sadece kendi claim'ini geri verir)")
    @DeleteMapping("/{id}/claim")
    @PreAuthorize("hasAnyRole('AGENT', 'AGENT_ADMIN')")
    public ResponseEntity<TicketResponseDTO> unclaimTicket(
                        @PathVariable Long id,
                        @RequestBody @Valid UnclaimRequestDTO dto,
                        @AuthenticationPrincipal Jwt jwt) {
        String agentId = jwt.getSubject();
        List<String> roles = JwtUtils.extractRoles(jwt);
        log.info("Bilet unclaim isteği. Bilet ID: {}, Agent: {}", id, agentId);
        Ticket ticket = ticketService.unclaimTicket(id, agentId, dto.getNote());
        log.info("Bilet başarıyla unclaim yapıldı. Bilet ID: {}", id);
        return ResponseEntity.ok(convertToDto(ticket, false, roles));
        }

    @Operation(summary = "Bileti agent'a manuel olarak ata (Agent Admin)",
            description = "Agent Admin rolüne sahip kullanıcılar, belirtilen bileti hedef agent'a atayabilir. Kapasite kontrolü yapılır.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Bilet başarıyla atandı"),
            @ApiResponse(responseCode = "400", description = "Hedef agent'ın limiti dolu veya bilet kapalı"),
            @ApiResponse(responseCode = "403", description = "Yalnızca AGENT_ADMIN atama yapabilir veya hedef agent yetkisiz"),
            @ApiResponse(responseCode = "404", description = "Bilet veya agent bulunamadı")
    })
    @PutMapping("/{id}/assign")
    @PreAuthorize("hasRole('AGENT_ADMIN')")
    public ResponseEntity<TicketResponseDTO> assignTicket(
            @Parameter(description = "Atanacak biletin ID'si", required = true)
            @PathVariable Long id,
            @RequestBody @Valid AssignTicketRequestDTO request,
            @AuthenticationPrincipal Jwt jwt) {

        String adminId = jwt.getSubject();
        List<String> roles = JwtUtils.extractRoles(jwt);

        log.info("Manuel atama isteği. Bilet: {}, Hedef Agent: {}, Admin: {}",
                id, request.getTargetAgentId(), adminId);

        Ticket ticket = ticketService.assignTicket(
                id,
                request.getTargetAgentId(),
                adminId,
                request.getNote()
        );

        return ResponseEntity.ok(convertToDto(ticket, false, roles));
    }

        @Operation(summary = "Bileti kapat (not zorunlu)")
        @PutMapping("/{id}/close")        @PreAuthorize("hasAnyRole('AGENT', 'AGENT_ADMIN')")
        public ResponseEntity<TicketResponseDTO> closeTicket(
                        @PathVariable Long id,
                        @RequestBody @Valid CloseTicketRequestDTO dto,
                        @AuthenticationPrincipal Jwt jwt) {
        String userId = jwt.getSubject();
        List<String> roles = JwtUtils.extractRoles(jwt);
        log.info("Bilet kapatma isteği. Bilet ID: {}, Kullanıcı: {}", id, userId);
        Ticket ticket = ticketService.closeTicket(id, dto.getNote(), userId, roles);
        log.info("Bilet başarıyla kapatıldı. Bilet ID: {}", id);
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
        log.info("Bilet statüsü güncelleme isteği. Bilet ID: {}, Yeni Statü: {}, Kullanıcı: {}", id, newStatus, userId);
        Ticket ticket = ticketService.updateTicketStatus(id, newStatus, userId, roles);
        log.info("Bilet statüsü güncellendi. Bilet ID: {}, Statü: {}", id, newStatus);
        return ResponseEntity.ok(convertToDto(ticket, false, roles));
    }

    @Operation(summary = "Bilet önceliği güncelle")
    @PutMapping("/{id}/priority")
    @PreAuthorize("hasAnyRole('AGENT', 'AGENT_ADMIN')")
    public ResponseEntity<TicketResponseDTO> updatePriority(
            @PathVariable Long id,
            @RequestBody Map<String, String> body,
            @AuthenticationPrincipal Jwt jwt) {
        String newPriority = body.get("priority");
        String userId = jwt.getSubject();
        List<String> roles = JwtUtils.extractRoles(jwt);
        log.info("Bilet önceliği güncelleme isteği. Bilet ID: {}, Yeni Öncelik: {}, Kullanıcı: {}", id, newPriority, userId);
        Ticket ticket = ticketService.updateTicketPriority(id, newPriority, userId, roles);
        log.info("Bilet önceliği güncellendi. Bilet ID: {}, Öncelik: {}", id, newPriority);
        return ResponseEntity.ok(convertToDto(ticket, false, roles));
    }

    @Operation(summary = "Bileti sil (AGENT_ADMIN yetkisi gerekir)")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('AGENT_ADMIN')")
    public ResponseEntity<Void> deleteTicket(
            @Parameter(description = "Silinecek biletin ID'si") @PathVariable Long id) {
        log.warn("Bilet silme isteği. Bilet ID: {}", id);
        ticketService.deleteTicket(id);
        log.warn("Bilet kalıcı olarak silindi. Bilet ID: {}", id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Ürüne ait biletleri listele — sayfalama + filtreleme")
    @GetMapping("/by-product/{productId}")
    public ResponseEntity<Page<TicketResponseDTO>> getTicketsByProduct(
            @PathVariable Long productId,
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0")   int page,
            @RequestParam(defaultValue = "20")  int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir,
            @RequestParam(required = false) List<String> status,
            @RequestParam(required = false) List<String> priority,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String agentId,
            @RequestParam(required = false) List<String> slaStatus,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) ZonedDateTime dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) ZonedDateTime dateTo) {

        String userId = jwt.getSubject();
        List<String> roles = JwtUtils.extractRoles(jwt);
        Sort sort = sortDir.equalsIgnoreCase("asc") ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
        PageRequest pageable = PageRequest.of(page, size, sort);
        TicketFilterDTO filter = TicketFilterDTO.builder()
                .statuses(status).priorities(priority).search(search)
                .agentId(agentId).slaStatuses(slaStatus)
                .createdAtFrom(dateFrom).createdAtTo(dateTo).build();

        Page<Ticket> tickets = ticketService.getTicketsByProductFiltered(productId, userId, roles, filter, pageable);
        return ResponseEntity.ok(tickets.map(t -> convertToDto(t, false, roles)));
    }

    @Operation(summary = "SLA zamanlayıcı bilgisi")
    @GetMapping("/{id}/sla-timer")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'AGENT', 'AGENT_ADMIN')")
    public ResponseEntity<Map<String, Object>> getSlaTimer(
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

        List<TicketAuditLogDTO> auditLogs = ticketAuditLogRepository.findByTicketIdOrderByCreatedAtDesc(ticket.getId()).stream()
                .map(log -> TicketAuditLogDTO.fromEntity(log,
                        userRepository.findById(log.getActorId())
                                .map(User::getFullName)
                                .orElse(log.getActorId())))
                .collect(Collectors.toList());

        TicketResponseDTO dto = TicketResponseDTO.fromEntity(ticket, hasCsat, productName, customerName, claimers);
        dto.setSlaInfo(ticketService.getSlaTimerInfo(ticket));
        dto.setAuditLogs(auditLogs);
        return dto;
    }
}
