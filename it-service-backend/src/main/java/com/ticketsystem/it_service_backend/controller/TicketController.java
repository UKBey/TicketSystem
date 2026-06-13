package com.ticketsystem.it_service_backend.controller;

import com.ticketsystem.it_service_backend.dto.AssignTicketRequestDTO;
import com.ticketsystem.it_service_backend.dto.CloseTicketRequestDTO;
import com.ticketsystem.it_service_backend.dto.PriorityChangeRequestDTO;
import com.ticketsystem.it_service_backend.dto.TicketFilterDTO;
import com.ticketsystem.it_service_backend.dto.TicketRequestDTO;
import com.ticketsystem.it_service_backend.dto.TicketResponseDTO;
import com.ticketsystem.it_service_backend.dto.TopicChangeRequestDTO;
import com.ticketsystem.it_service_backend.dto.StatusUpdateRequestDTO;
import com.ticketsystem.it_service_backend.dto.UnclaimRequestDTO;
import com.ticketsystem.it_service_backend.entity.Ticket;
import com.ticketsystem.it_service_backend.service.TicketCommandService;
import com.ticketsystem.it_service_backend.service.TicketDtoAssembler;
import com.ticketsystem.it_service_backend.service.TicketQueryService;
import com.ticketsystem.it_service_backend.service.TicketService;
import com.ticketsystem.it_service_backend.util.AuthRoles;
import com.ticketsystem.it_service_backend.util.JwtUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import com.ticketsystem.it_service_backend.util.Pageables;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

/**
 * Main REST controller for the ticket lifecycle.
 *
 * <p>Exposes role-specific endpoints for customers, agents, agent admins and managers:
 * creation, listing, claiming, assignment, status/priority/topic changes and closing.
 * Business rules are delegated to {@link TicketService} (creation, lookup, claim,
 * delete), {@link TicketQueryService} (listing/filtering) and
 * {@link TicketCommandService} (status/priority/topic mutations); this class only
 * handles HTTP/JSON mapping and role-based authorization.
 */
@Log4j2
@Tag(name = "Bilet Yönetimi", description = "Destek biletlerinin oluşturulması, listelenmesi, sahiplenilmesi ve yönetimi")
@RestController
@RequestMapping("/api/v1/tickets")
@RequiredArgsConstructor
@Validated
public class TicketController {

    private final TicketService ticketService;
    private final TicketQueryService ticketQueryService;
    private final TicketCommandService ticketCommandService;
    private final TicketDtoAssembler ticketDtoAssembler;

    /**
     * Creates a new ticket and starts the jBPM workflow process.
     *
     * @param dto title, description, priority, product and topic information
     * @return DTO representation of the created ticket
     */
    @Operation(summary = "Yeni bilet oluştur")
    @PostMapping
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<TicketResponseDTO> createTicket(@Valid @RequestBody TicketRequestDTO dto,
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
                .topicId(dto.getTopicId())
                .build();

        Ticket saved = ticketService.createTicket(ticket, customerId);
        log.info("Bilet başarıyla oluşturuldu. Bilet ID: {}", saved.getId());
        return ResponseEntity.ok(convertToDto(saved, false, roles));
    }

    /**
     * Lists tickets paginated according to role and filter criteria.
     *
     * <p>Customers see only their own tickets; agents and agent admins see all active
     * tickets they are authorized for.
     *
     * @param page page index (0-based)
     * @param size page size (1-500)
     * @param sortBy sort field
     * @param sortDir {@code asc} / {@code desc}
     * @return paginated list of ticket DTOs
     */
    @Operation(summary = "Biletleri listele — sayfalama + filtreleme (role göre filtrelenir)")
    @GetMapping
    @PreAuthorize("hasAnyRole('CUSTOMER', 'AGENT', 'LEAD_AGENT', 'MANAGER', 'ADMIN')")
    public ResponseEntity<Page<TicketResponseDTO>> getTickets(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0")   int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(500) int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir,
            @RequestParam(required = false) List<String> status,
            @RequestParam(required = false) List<String> priority,
            @RequestParam(required = false) @Size(max = 100, message = "Arama metni en fazla 100 karakter olabilir") String search,
            @RequestParam(required = false) List<Long> productId,
            @RequestParam(required = false) List<String> agentId,
            @RequestParam(required = false) List<Long> topicId,
            @RequestParam(required = false) List<String> slaStatus,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) ZonedDateTime dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) ZonedDateTime dateTo) {

        String userId = jwt.getSubject();
        List<String> roles = JwtUtils.extractRoles(jwt);
        PageRequest pageable = Pageables.of(page, size, sortBy, sortDir);
        TicketFilterDTO filter = TicketFilterDTO.builder()
                .statuses(status).priorities(priority).search(search)
                .productIds(productId).agentIds(agentId).topicIds(topicId).slaStatuses(slaStatus)
                .createdAtFrom(dateFrom).createdAtTo(dateTo).build();

        Page<Ticket> tickets;
        // Operasyonel personel (AGENT/LEAD_AGENT) ile global roller (ADMIN/MANAGER)
        // takım/tüm bilet görünümünü alır; saf müşteri yalnızca kendi biletlerini görür.
        if (AuthRoles.isAgentLevel(roles) || AuthRoles.isGlobal(roles)) {
            tickets = ticketQueryService.getTeamTicketsFiltered(userId, roles, filter, pageable);
        } else {
            tickets = ticketQueryService.getCustomerTicketsFiltered(userId, filter, pageable);
        }
        return ResponseEntity.ok(tickets.map(t -> convertToDto(t, false, roles)));
    }

    /**
     * Lists pool tickets ({@code NEW}, not yet claimed) in a paginated manner.
     *
     * @param page page index
     * @param size page size (1-500)
     * @return paginated list of pool tickets
     */
    @Operation(summary = "Havuzdaki biletleri listele — sayfalama + filtreleme (NEW statüsü)")
    @GetMapping("/pool")
    @PreAuthorize("hasAnyRole('AGENT', 'LEAD_AGENT')")
    public ResponseEntity<Page<TicketResponseDTO>> getPoolTickets(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0")   int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(500) int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir,
            @RequestParam(required = false) List<String> priority,
            @RequestParam(required = false) @Size(max = 100, message = "Arama metni en fazla 100 karakter olabilir") String search,
            @RequestParam(required = false) List<Long> productId,
            @RequestParam(required = false) List<String> agentId,
            @RequestParam(required = false) List<Long> topicId,
            @RequestParam(required = false) List<String> slaStatus,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) ZonedDateTime dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) ZonedDateTime dateTo) {

        String userId = jwt.getSubject();
        List<String> roles = JwtUtils.extractRoles(jwt);
        PageRequest pageable = Pageables.of(page, size, sortBy, sortDir);
        TicketFilterDTO filter = TicketFilterDTO.builder()
                .priorities(priority).search(search).productIds(productId)
                .agentIds(agentId).topicIds(topicId).slaStatuses(slaStatus)
                .createdAtFrom(dateFrom).createdAtTo(dateTo).build();

        Page<Ticket> pool = ticketQueryService.getPoolTicketsFiltered(userId, roles, filter, pageable);
        return ResponseEntity.ok(pool.map(t -> convertToDto(t, false, roles)));
    }

    /**
     * Lists the tickets claimed by the authenticated agent in a paginated manner.
     *
     * @param page page index
     * @param size page size (1-500)
     * @return paginated list of tickets assigned to the agent
     */
    @Operation(summary = "Ajanın claim aldığı biletleri listele — sayfalama + filtreleme")
    @GetMapping("/my-assigned")
    public ResponseEntity<Page<TicketResponseDTO>> getMyAssignedTickets(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0")   int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(500) int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir,
            @RequestParam(required = false) List<String> status,
            @RequestParam(required = false) List<String> priority,
            @RequestParam(required = false) @Size(max = 100, message = "Arama metni en fazla 100 karakter olabilir") String search,
            @RequestParam(required = false) List<Long> productId,
            @RequestParam(required = false) List<String> agentId,
            @RequestParam(required = false) List<Long> topicId,
            @RequestParam(required = false) List<String> slaStatus,
            @RequestParam(required = false) List<String> csatRating,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) ZonedDateTime dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) ZonedDateTime dateTo) {

        String agentUserId = jwt.getSubject();
        List<String> roles = JwtUtils.extractRoles(jwt);
        // CSAT filtre/sıralaması yalnızca ADMIN/MANAGER için geçerli; diğer roller için elenir.
        boolean canSeeCsat = AuthRoles.isGlobal(roles);
        String effectiveSortBy = (!canSeeCsat && "csatRating".equals(sortBy)) ? "createdAt" : sortBy;
        PageRequest pageable = Pageables.of(page, size, effectiveSortBy, sortDir);
        TicketFilterDTO filter = TicketFilterDTO.builder()
                .statuses(status).priorities(priority).search(search)
                .productIds(productId).agentIds(agentId).topicIds(topicId).slaStatuses(slaStatus)
                .csatRatings(canSeeCsat ? csatRating : null)
                .createdAtFrom(dateFrom).createdAtTo(dateTo).build();

        Page<Ticket> tickets = ticketQueryService.getAgentClaimedTicketsFiltered(agentUserId, filter, pageable);
        return ResponseEntity.ok(tickets.map(t -> convertToDto(t, false, roles)));
    }

    /**
     * Lists active tickets across the products the agent is authorized for, in a paginated manner.
     *
     * @param page page index
     * @param size page size (1-500)
     * @return paginated list of tickets in the team view
     */
    @Operation(summary = "Ajanın yetkili ürünlerindeki aktif biletleri listele — sayfalama + filtreleme")
    @GetMapping("/team")
    @PreAuthorize("hasAnyRole('AGENT', 'LEAD_AGENT')")
    public ResponseEntity<Page<TicketResponseDTO>> getTeamTickets(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0")   int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(500) int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir,
            @RequestParam(required = false) List<String> status,
            @RequestParam(required = false) List<String> priority,
            @RequestParam(required = false) @Size(max = 100, message = "Arama metni en fazla 100 karakter olabilir") String search,
            @RequestParam(required = false) List<Long> productId,
            @RequestParam(required = false) List<String> agentId,
            @RequestParam(required = false) List<Long> topicId,
            @RequestParam(required = false) List<String> slaStatus,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) ZonedDateTime dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) ZonedDateTime dateTo) {

        String userId = jwt.getSubject();
        List<String> roles = JwtUtils.extractRoles(jwt);
        PageRequest pageable = Pageables.of(page, size, sortBy, sortDir);
        TicketFilterDTO filter = TicketFilterDTO.builder()
                .statuses(status).priorities(priority).search(search).productIds(productId)
                .agentIds(agentId).topicIds(topicId).slaStatuses(slaStatus)
                .createdAtFrom(dateFrom).createdAtTo(dateTo).build();

        Page<Ticket> tickets = ticketQueryService.getTeamTicketsFiltered(userId, roles, filter, pageable);
        return ResponseEntity.ok(tickets.map(t -> convertToDto(t, false, roles)));
    }

    /**
     * Ticket list for the Agent/Agent Admin "All Tickets" page, including every status.
     *
     * @param page page index
     * @param size page size (1-500)
     * @return paginated list of all accessible tickets
     */
    @Operation(summary = "Yetkili olunan tüm ürünlerdeki tüm statülerdeki biletler — sayfalama + filtreleme",
            description = "Agent/Agent Admin için 'All Tickets' sayfasının veri kaynağı. NEW ve CLOSED dahil tüm statüleri içerir.")
    @GetMapping("/all")
    @PreAuthorize("hasAnyRole('AGENT', 'LEAD_AGENT', 'MANAGER', 'ADMIN')")
    public ResponseEntity<Page<TicketResponseDTO>> getAllAccessibleTickets(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0")   int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(500) int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir,
            @RequestParam(required = false) List<String> status,
            @RequestParam(required = false) List<String> priority,
            @RequestParam(required = false) @Size(max = 100, message = "Arama metni en fazla 100 karakter olabilir") String search,
            @RequestParam(required = false) List<Long> productId,
            @RequestParam(required = false) List<String> agentId,
            @RequestParam(required = false) List<Long> topicId,
            @RequestParam(required = false) List<String> slaStatus,
            @RequestParam(required = false) List<String> csatRating,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) ZonedDateTime dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) ZonedDateTime dateTo) {

        String userId = jwt.getSubject();
        List<String> roles = JwtUtils.extractRoles(jwt);
        // CSAT filtre/sıralaması yalnızca ADMIN/MANAGER için geçerli; diğer roller için elenir.
        boolean canSeeCsat = AuthRoles.isGlobal(roles);
        String effectiveSortBy = (!canSeeCsat && "csatRating".equals(sortBy)) ? "createdAt" : sortBy;
        PageRequest pageable = Pageables.of(page, size, effectiveSortBy, sortDir);
        TicketFilterDTO filter = TicketFilterDTO.builder()
                .statuses(status).priorities(priority).search(search).productIds(productId)
                .agentIds(agentId).topicIds(topicId).slaStatuses(slaStatus)
                .csatRatings(canSeeCsat ? csatRating : null)
                .createdAtFrom(dateFrom).createdAtTo(dateTo).build();

        Page<Ticket> tickets = ticketQueryService.getAllAccessibleTicketsFiltered(userId, roles, filter, pageable);
        return ResponseEntity.ok(tickets.map(t -> convertToDto(t, false, roles)));
    }

    /**
     * Returns the detail of the specified ticket; ownership and authorization checks live in the service layer.
     *
     * @param id ticket identifier
     * @return ticket DTO representation (with audit log, claims and SLA information)
     */
    @Operation(summary = "Bilet detayı getir")
    @GetMapping("/{id}")
    public ResponseEntity<TicketResponseDTO> getTicket(
            @PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        String userId = jwt.getSubject();
        List<String> roles = JwtUtils.extractRoles(jwt);
        Ticket ticket = ticketService.getTicketWithAuth(id, userId, roles);
        return ResponseEntity.ok(convertToDto(ticket, false, roles));
    }

    /**
     * Claims the ticket for the calling agent. Allowed in every status except {@code CLOSED}.
     *
     * @param id identifier of the ticket to claim
     * @return ticket DTO with the updated claim information
     */
    @Operation(summary = "Bileti claim al (CLOSED hariç her statüdeki bilet sahiplenebilir)")
    @PutMapping("/{id}/claim")
    @PreAuthorize("hasAnyRole('AGENT', 'LEAD_AGENT')")
    public ResponseEntity<TicketResponseDTO> claimTicket(
            @PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        String agentId = jwt.getSubject();
        List<String> roles = JwtUtils.extractRoles(jwt);
        log.info("Bilet claim isteği. Bilet ID: {}, Agent: {}", id, agentId);
        Ticket ticket = ticketService.claimTicket(id, agentId);
        log.info("Bilet başarıyla claim alındı. Bilet ID: {}", id);
        return ResponseEntity.ok(convertToDto(ticket, false, roles));
    }

    /**
     * Releases the calling agent's claim; only the agent's own claim is given back.
     *
     * @param id ticket identifier
     * @param dto reason code for the release and an optional note
     * @return ticket DTO with the updated claim list
     */
    @Operation(summary = "Claim'i bırak (sadece kendi claim'ini geri verir)")
    @DeleteMapping("/{id}/claim")
    @PreAuthorize("hasAnyRole('AGENT', 'LEAD_AGENT')")
    public ResponseEntity<TicketResponseDTO> unclaimTicket(
                        @PathVariable Long id,
                        @RequestBody @Valid UnclaimRequestDTO dto,
                        @AuthenticationPrincipal Jwt jwt) {
        String agentId = jwt.getSubject();
        List<String> roles = JwtUtils.extractRoles(jwt);
        log.info("Bilet unclaim isteği. Bilet ID: {}, Agent: {}", id, agentId);
        Ticket ticket = ticketService.unclaimTicket(id, agentId, dto.getReasonCode(), dto.getNote());
        log.info("Bilet başarıyla unclaim yapıldı. Bilet ID: {}", id);
        return ResponseEntity.ok(convertToDto(ticket, false, roles));
        }

    /**
     * Performs a manual assignment of the ticket to the target agent by an Agent Admin.
     *
     * @param id identifier of the ticket to assign
     * @param request target agent identifier and an optional note
     * @return ticket DTO after the assignment
     */
    @Operation(summary = "Bileti agent'a manuel olarak ata (Agent Admin)",
            description = "Agent Admin rolüne sahip kullanıcılar, belirtilen bileti hedef agent'a atayabilir. Kapasite kontrolü yapılır.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Bilet başarıyla atandı"),
            @ApiResponse(responseCode = "400", description = "Hedef agent'ın limiti dolu veya bilet kapalı"),
            @ApiResponse(responseCode = "403", description = "Yalnızca LEAD_AGENT veya ADMIN atama yapabilir veya hedef agent yetkisiz"),
            @ApiResponse(responseCode = "404", description = "Bilet veya agent bulunamadı")
    })
    @PutMapping("/{id}/assign")
    @PreAuthorize("hasAnyRole('LEAD_AGENT', 'ADMIN')")
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

    /**
     * Moves the ticket into the {@code CLOSED} status; the reason code and an explanatory note are required.
     *
     * @param id identifier of the ticket to close
     * @param dto closing reason code and note
     * @return DTO of the closed ticket
     */
        @Operation(summary = "Bileti kapat (not zorunlu)")
        @PutMapping("/{id}/close")        @PreAuthorize("hasAnyRole('AGENT', 'LEAD_AGENT')")
        public ResponseEntity<TicketResponseDTO> closeTicket(
                        @PathVariable Long id,
                        @RequestBody @Valid CloseTicketRequestDTO dto,
                        @AuthenticationPrincipal Jwt jwt) {
        String userId = jwt.getSubject();
        List<String> roles = JwtUtils.extractRoles(jwt);
        log.info("Bilet kapatma isteği. Bilet ID: {}, Kullanıcı: {}", id, userId);
        Ticket ticket = ticketCommandService.closeTicket(id, dto.getReasonCode(), dto.getNote(), userId, roles);
        log.info("Bilet başarıyla kapatıldı. Bilet ID: {}", id);
        return ResponseEntity.ok(convertToDto(ticket, false, roles));
    }

    /**
     * Updates the ticket's status; a reason code is required when transitioning to {@code RESOLVED}.
     *
     * @param id ticket identifier
     * @param body new status, reason code and optional note
     * @return DTO of the updated ticket
     */
    @Operation(summary = "Bilet statüsü güncelle (RESOLVED'a geçişte reasonCode zorunlu)")
    @PutMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'AGENT', 'LEAD_AGENT')")
    public ResponseEntity<TicketResponseDTO> updateStatus(
            @PathVariable Long id,
            @RequestBody @Valid StatusUpdateRequestDTO body,
            @AuthenticationPrincipal Jwt jwt) {
        String newStatus = body.getStatus();
        String userId = jwt.getSubject();
        List<String> roles = JwtUtils.extractRoles(jwt);
        log.info("Bilet statüsü güncelleme isteği. Bilet ID: {}, Yeni Statü: {}, Kullanıcı: {}", id, newStatus, userId);
        Ticket ticket = ticketCommandService.updateTicketStatus(id, newStatus, body.getReasonCode(), body.getNote(), userId, roles);
        log.info("Bilet statüsü güncellendi. Bilet ID: {}, Statü: {}", id, newStatus);
        return ResponseEntity.ok(convertToDto(ticket, false, roles));
    }

    /**
     * Updates the ticket's priority; a reason code is required (a note is also required if it is {@code OTHER}).
     *
     * @param id ticket identifier
     * @param dto new priority, reason code and optional note
     * @return DTO of the updated ticket
     */
    @Operation(summary = "Bilet önceliği güncelle (sebep kodu zorunlu, OTHER ise not zorunlu)")
    @PutMapping("/{id}/priority")
    @PreAuthorize("hasAnyRole('AGENT', 'LEAD_AGENT')")
    public ResponseEntity<TicketResponseDTO> updatePriority(
            @PathVariable Long id,
            @RequestBody @Valid PriorityChangeRequestDTO dto,
            @AuthenticationPrincipal Jwt jwt) {
        String userId = jwt.getSubject();
        List<String> roles = JwtUtils.extractRoles(jwt);
        log.info("Bilet önceliği güncelleme isteği. Bilet ID: {}, Yeni Öncelik: {}, Sebep: {}, Kullanıcı: {}",
                id, dto.getPriority(), dto.getReasonCode(), userId);
        Ticket ticket = ticketCommandService.updateTicketPriority(id, dto.getPriority(), dto.getReasonCode(), dto.getNote(), userId, roles);
        log.info("Bilet önceliği güncellendi. Bilet ID: {}, Öncelik: {}", id, dto.getPriority());
        return ResponseEntity.ok(convertToDto(ticket, false, roles));
    }

    /**
     * Changes the ticket's topic to an active topic that belongs to the same product.
     *
     * <p>A reason code is required; a note is also required when the reason is {@code OTHER}.
     *
     * @param id ticket identifier
     * @param dto new topic identifier, reason code and optional note
     * @return DTO of the updated ticket
     */
    @Operation(summary = "Bilet konusunu güncelle (aynı ürüne bağlı aktif bir topic; sebep kodu zorunlu, OTHER ise not zorunlu)")
    @PutMapping("/{id}/topic")
    @PreAuthorize("hasAnyRole('AGENT', 'LEAD_AGENT')")
    public ResponseEntity<TicketResponseDTO> updateTopic(
            @PathVariable Long id,
            @RequestBody @Valid TopicChangeRequestDTO dto,
            @AuthenticationPrincipal Jwt jwt) {
        String userId = jwt.getSubject();
        List<String> roles = JwtUtils.extractRoles(jwt);
        log.info("Bilet konusu güncelleme isteği. Bilet ID: {}, Yeni Topic: {}, Sebep: {}, Kullanıcı: {}",
                id, dto.getTopicId(), dto.getReasonCode(), userId);
        Ticket ticket = ticketCommandService.updateTicketTopic(id, dto.getTopicId(), dto.getReasonCode(), dto.getNote(), userId, roles);
        log.info("Bilet konusu güncellendi. Bilet ID: {}, Topic: {}", id, dto.getTopicId());
        return ResponseEntity.ok(convertToDto(ticket, false, roles));
    }

    /**
     * Permanently deletes the ticket from the database; only callable by {@code ADMIN}.
     *
     * @param id identifier of the ticket to delete
     * @return {@code 204 No Content}
     */
    @Operation(summary = "Bileti sil (ADMIN yetkisi gerekir)")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteTicket(
            @Parameter(description = "Silinecek biletin ID'si") @PathVariable Long id) {
        log.warn("Bilet silme isteği. Bilet ID: {}", id);
        ticketService.deleteTicket(id);
        log.warn("Bilet kalıcı olarak silindi. Bilet ID: {}", id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Lists the tickets for the given product, paginated and filtered by role-based authorization.
     *
     * @param productId product identifier
     * @param page page index
     * @param size page size (1-500)
     * @return paginated list of tickets for the product
     */
    @Operation(summary = "Ürüne ait biletleri listele — sayfalama + filtreleme")
    @GetMapping("/by-product/{productId}")
    public ResponseEntity<Page<TicketResponseDTO>> getTicketsByProduct(
            @PathVariable Long productId,
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0")   int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(500) int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir,
            @RequestParam(required = false) List<String> status,
            @RequestParam(required = false) List<String> priority,
            @RequestParam(required = false) @Size(max = 100, message = "Arama metni en fazla 100 karakter olabilir") String search,
            @RequestParam(required = false) List<String> agentId,
            @RequestParam(required = false) List<Long> topicId,
            @RequestParam(required = false) List<String> slaStatus,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) ZonedDateTime dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) ZonedDateTime dateTo) {

        String userId = jwt.getSubject();
        List<String> roles = JwtUtils.extractRoles(jwt);
        PageRequest pageable = Pageables.of(page, size, sortBy, sortDir);
        TicketFilterDTO filter = TicketFilterDTO.builder()
                .statuses(status).priorities(priority).search(search)
                .agentIds(agentId).topicIds(topicId).slaStatuses(slaStatus)
                .createdAtFrom(dateFrom).createdAtTo(dateTo).build();

        Page<Ticket> tickets = ticketQueryService.getTicketsByProductFiltered(productId, userId, roles, filter, pageable);
        return ResponseEntity.ok(tickets.map(t -> convertToDto(t, false, roles)));
    }

    /**
     * Returns the ticket's SLA timer information (remaining time, target, breach status, etc.).
     *
     * @param id ticket identifier
     * @return key-value map containing the SLA information
     */
    @Operation(summary = "SLA zamanlayıcı bilgisi")
    @GetMapping("/{id}/sla-timer")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'AGENT', 'LEAD_AGENT', 'MANAGER')")
    public ResponseEntity<Map<String, Object>> getSlaTimer(
            @PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        String userId = jwt.getSubject();
        List<String> roles = JwtUtils.extractRoles(jwt);
        Ticket ticket = ticketService.getTicketWithAuth(id, userId, roles);
        return ResponseEntity.ok(ticketService.getSlaTimerInfo(ticket));
    }

    // -----------------------------------------------------------------
    // DTO dönüşümü — derleme mantığı (isim çözümü, claim/audit/CSAT) servis
    // katmanındaki TicketDtoAssembler'a taşındı; controller yalnızca delege eder.
    // -----------------------------------------------------------------

    private TicketResponseDTO convertToDto(Ticket ticket, boolean hasCsat, List<String> roles) {
        return ticketDtoAssembler.toDto(ticket, hasCsat, roles);
    }
}
