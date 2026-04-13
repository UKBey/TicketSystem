package com.ticketsystem.it_service_backend.controller;

import com.ticketsystem.it_service_backend.dto.CsatDTO;
import com.ticketsystem.it_service_backend.dto.ResolutionNoteRequestDTO;
import com.ticketsystem.it_service_backend.dto.ResolutionNoteResponseDTO;
import com.ticketsystem.it_service_backend.dto.TicketRequestDTO;
import com.ticketsystem.it_service_backend.dto.TicketResponseDTO;
import com.ticketsystem.it_service_backend.dto.WorklogRequestDTO;
import com.ticketsystem.it_service_backend.dto.WorklogResponseDTO;
import com.ticketsystem.it_service_backend.entity.Csat;
import com.ticketsystem.it_service_backend.entity.ResolutionNote;
import com.ticketsystem.it_service_backend.entity.Ticket;
import com.ticketsystem.it_service_backend.entity.TicketWorklog;
import com.ticketsystem.it_service_backend.entity.User;
import com.ticketsystem.it_service_backend.entity.Product;
import com.ticketsystem.it_service_backend.repository.UserRepository;
import com.ticketsystem.it_service_backend.repository.ProductRepository;
import com.ticketsystem.it_service_backend.service.CsatService;
import com.ticketsystem.it_service_backend.service.ResolutionNoteService;
import com.ticketsystem.it_service_backend.service.TicketService;
import com.ticketsystem.it_service_backend.service.WorklogService;
import com.ticketsystem.it_service_backend.util.JwtUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.HttpStatus;
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
    private final CsatService csatService;
    private final WorklogService worklogService;
    private final ResolutionNoteService resolutionNoteService;
    private final UserRepository userRepository;
    private final ProductRepository productRepository;

    // ── BİLET İŞLEMLERİ ──────────────────────────────────────────────────────────

    // 1. Yeni Bilet Oluştur (Müşteri)
    @Operation(summary = "Yeni bilet oluştur", description = "Sadece Müşteri rolündeki kullanıcılar bilet açabilir.")
    @PostMapping
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<TicketResponseDTO> createTicket(@RequestBody TicketRequestDTO ticketRequest,
            @AuthenticationPrincipal Jwt jwt) {
        String customerId = jwt.getSubject();

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

        return ResponseEntity.ok(convertToDto(savedTicket));
    }

    // 2. Biletleri Listele (Rol bazlı)
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
                .map(this::convertToDto)
                .collect(Collectors.toList()));
    }

    // 3. Havuzdaki Atanmamış Biletleri Getir (Ajan/Yönetici)
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
                .map(this::convertToDto)
                .collect(Collectors.toList()));
    }

    // 4. Ajanın Kendi Üzerindeki Biletleri Getir
    @Operation(summary = "Ajanın üzerindeki biletleri listele", description = "Giriş yapan ajana atanmış aktif biletler.")
    @GetMapping("/my-assigned")
    public ResponseEntity<List<TicketResponseDTO>> getMyAssignedTickets(@AuthenticationPrincipal Jwt jwt) {
        String agentId = jwt.getSubject();

        log.info("Ajan üzerindeki biletleri listeleme isteği. Ajan ID: {}", agentId);

        List<Ticket> tickets = ticketService.getAgentAssignedTickets(agentId);

        log.info("Ajan (ID: {}) üzerinde {} adet bilet bulundu.", agentId, tickets.size());

        return ResponseEntity.ok(tickets.stream()
                .map(this::convertToDto)
                .collect(Collectors.toList()));
    }

    // 5. Tekil Bilet Detayı (Güvenli)
    @Operation(summary = "Bilet detayı getir", description = "ID ile bilet detayını çeker. Yetki kontrolü yapılır.")
    @GetMapping("/{id}")
    public ResponseEntity<TicketResponseDTO> getTicket(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        String userId = jwt.getSubject();
        List<String> roles = JwtUtils.extractRoles(jwt);

        log.info("Bilet detayı isteği. Bilet ID: {}, Kullanıcı: {}", id, userId);

        Ticket ticket = ticketService.getTicketWithAuth(id, userId, roles);
        boolean hasCsat = csatService.hasCsat(id);

        log.info("Bilet detayı başarıyla çekildi. Bilet ID: {}, CSAT Mevcut: {}", id, hasCsat);

        return ResponseEntity.ok(convertToDto(ticket, hasCsat));
    }

    // 6. Havuzdan Bilet Üzerine Alma (Claim)
    @Operation(summary = "Bileti sahiplen (Claim)", description = "Havuzdaki bir bileti ajanın kendi üzerine almasını sağlar.")
    @PutMapping("/{id}/claim")
    @PreAuthorize("hasRole('AGENT')")
    public ResponseEntity<TicketResponseDTO> claimTicket(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        String agentId = jwt.getSubject();

        log.info("Bileti sahiplenme (claim) isteği. Bilet ID: {}, Ajan ID: {}", id, agentId);

        Ticket ticket = ticketService.claimTicket(id, agentId);

        log.info("Bilet başarıyla sahiplenildi. Bilet ID: {}, Ajan ID: {}", id, agentId);

        return ResponseEntity.ok(convertToDto(ticket));
    }

    // 7. Statü Güncelleme
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
        boolean hasCsat = csatService.hasCsat(id);

        log.info("Bilet statüsü başarıyla güncellendi. Bilet ID: {}, Yeni Statü: {}", id, ticket.getStatus());

        return ResponseEntity.ok(convertToDto(ticket, hasCsat));
    }

    // 8. Bileti Sil (Sadece Yönetici)
    @Operation(summary = "Bileti sil", description = "Sadece Yönetici (MANAGER) bilet silebilir.")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<Void> deleteTicket(@PathVariable Long id) {
        log.info("Bilet silme isteği. Bilet ID: {}", id);

        ticketService.deleteTicket(id);

        log.info("Bilet başarıyla silindi. Bilet ID: {}", id);

        return ResponseEntity.noContent().build();
    }

    // ── CSAT ─────────────────────────────────────────────────────────────────────

    // 9. CSAT Memnuniyet Anketi Gönder
    @Operation(summary = "CSAT anketini gönder", description = "Çözülen bilet için müşteri memnuniyet anketi doldurur.")
    @PostMapping("/{id}/csat")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<Csat> submitCsat(
            @PathVariable Long id,
            @RequestBody CsatDTO csatDTO,
            @AuthenticationPrincipal Jwt jwt) {
        String userId = jwt.getSubject();
        List<String> roles = JwtUtils.extractRoles(jwt);
        return ResponseEntity.ok(csatService.submitCsat(id, csatDTO, userId, roles));
    }

    // 10. CSAT Detayını Getir
    @Operation(summary = "CSAT detayını getir", description = "Belirli bir biletin anket sonucunu getirir.")
    @GetMapping("/{id}/csat")
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<Csat> getCsat(
            @PathVariable Long id,
            @AuthenticationPrincipal Jwt jwt) {
        String userId = jwt.getSubject();
        List<String> roles = JwtUtils.extractRoles(jwt);
        return ResponseEntity.ok(csatService.getCsatByTicketId(id, userId, roles));
    }

    // 11. Tüm CSAT Anketlerini Listele (Sadece Yönetici)
    @Operation(summary = "Tüm CSAT anketlerini listele", description = "Sistemdeki tüm müşteri memnuniyet sonuçlarını getirir.")
    @GetMapping("/all-csats")
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<List<Csat>> getAllCsats() {
        return ResponseEntity.ok(csatService.getAllCsats());
    }

    // ── WORKLOG ───────────────────────────────────────────────────────────────────

    // 12. Worklog Ekle (Sadece atanmış Agent)
    @Operation(summary = "Worklog ekle", description = "Bir bilete iş kaydı ekler. Yalnızca biletin atandığı agent işlem yapabilir.")
    @PostMapping("/{id}/worklogs")
    @PreAuthorize("hasRole('AGENT')")
    public ResponseEntity<WorklogResponseDTO> addWorklog(
            @PathVariable Long id,
            @RequestBody WorklogRequestDTO dto,
            @AuthenticationPrincipal Jwt jwt) {
        String agentId = jwt.getSubject();
        log.info("Worklog ekleme isteği. Bilet ID: {}, Agent: {}", id, agentId);

        TicketWorklog saved = worklogService.addWorklog(id, dto, agentId);

        log.info("Worklog başarıyla oluşturuldu. Worklog ID: {}", saved.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(WorklogResponseDTO.fromEntity(saved));
    }

    // 13. Bilete Ait Worklogları Listele (Agent kendi bileti / Manager hepsi)
    @Operation(summary = "Bilete ait worklogları listele", description = "Bir biletin iş kayıtlarını getirir. Agent yalnızca kendine atanmış bileti görebilir.")
    @GetMapping("/{id}/worklogs")
    @PreAuthorize("hasAnyRole('AGENT', 'MANAGER')")
    public ResponseEntity<List<WorklogResponseDTO>> getWorklogsByTicket(
            @PathVariable Long id,
            @AuthenticationPrincipal Jwt jwt) {
        String userId = jwt.getSubject();
        List<String> roles = JwtUtils.extractRoles(jwt);

        log.info("Worklog listeleme isteği. Bilet ID: {}, Kullanıcı: {}", id, userId);

        List<TicketWorklog> worklogs = worklogService.getWorklogsByTicket(id, userId, roles);

        return ResponseEntity.ok(worklogs.stream()
                .map(WorklogResponseDTO::fromEntity)
                .collect(Collectors.toList()));
    }

    // 14. Tüm Worklogları Listele (Sadece Manager)
    @Operation(summary = "Tüm worklogları listele", description = "Sistemdeki tüm iş kayıtlarını getirir. Yalnızca Manager erişebilir.")
    @GetMapping("/all-worklogs")
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<List<WorklogResponseDTO>> getAllWorklogs() {
        log.info("Tüm worklogları listeleme isteği (Manager).");

        List<TicketWorklog> worklogs = worklogService.getAllWorklogs();

        log.info("Toplam {} worklog listelendi.", worklogs.size());

        return ResponseEntity.ok(worklogs.stream()
                .map(WorklogResponseDTO::fromEntity)
                .collect(Collectors.toList()));
    }

    // 15. Worklog Güncelle (Agent yalnızca kendi worklogu)
    @Operation(summary = "Worklog güncelle", description = "Bir iş kaydını günceller. Yalnızca worklogu oluşturan agent güncelleyebilir.")
    @PutMapping("/{id}/worklogs/{worklogId}")
    @PreAuthorize("hasRole('AGENT')")
    public ResponseEntity<WorklogResponseDTO> updateWorklog(
            @PathVariable Long id,
            @PathVariable Long worklogId,
            @RequestBody WorklogRequestDTO dto,
            @AuthenticationPrincipal Jwt jwt) {
        String agentId = jwt.getSubject();

        log.info("Worklog güncelleme isteği. Bilet ID: {}, Worklog ID: {}, Agent: {}", id, worklogId, agentId);

        TicketWorklog updated = worklogService.updateWorklog(id, worklogId, dto, agentId);

        log.info("Worklog başarıyla güncellendi. Worklog ID: {}", worklogId);
        return ResponseEntity.ok(WorklogResponseDTO.fromEntity(updated));
    }

    // 16. Worklog Sil (Agent kendi worklogu / Manager hepsi)
    @Operation(summary = "Worklog sil", description = "Bir iş kaydını siler. Agent yalnızca kendi oluşturduğu worklogu silebilir, Manager hepsini silebilir.")
    @DeleteMapping("/{id}/worklogs/{worklogId}")
    @PreAuthorize("hasAnyRole('AGENT', 'MANAGER')")
    public ResponseEntity<Void> deleteWorklog(
            @PathVariable Long id,
            @PathVariable Long worklogId,
            @AuthenticationPrincipal Jwt jwt) {
        String userId = jwt.getSubject();
        List<String> roles = JwtUtils.extractRoles(jwt);

        log.info("Worklog silme isteği. Bilet ID: {}, Worklog ID: {}, Kullanıcı: {}", id, worklogId, userId);

        worklogService.deleteWorklog(worklogId, userId, roles);

        log.info("Worklog başarıyla silindi. Worklog ID: {}", worklogId);
        return ResponseEntity.noContent().build();
    }

    // ── ÇÖZÜM NOTU ───────────────────────────────────────────────────────────────

    // 16. Çözüm Notu Oluştur (Sadece claim'li Agent — ilk kez)
    @Operation(summary = "Çözüm notu oluştur",
            description = "Bilete ilk çözüm notunu ekler. Yalnızca bileti üzerine almış (claim'li) agent işlem yapabilir. Bilete daha önce not eklenmiş olmamalı.")
    @PostMapping("/{id}/resolution-note")
    @PreAuthorize("hasRole('AGENT')")
    public ResponseEntity<ResolutionNoteResponseDTO> createResolutionNote(
            @PathVariable Long id,
            @RequestBody ResolutionNoteRequestDTO dto,
            @AuthenticationPrincipal Jwt jwt) {
        String agentId = jwt.getSubject();
        log.info("Çözüm notu oluşturma isteği. Bilet ID: {}, Agent: {}", id, agentId);

        ResolutionNote saved = resolutionNoteService.createResolutionNote(id, dto, agentId);

        log.info("Çözüm notu başarıyla oluşturuldu. Not ID: {}", saved.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(ResolutionNoteResponseDTO.fromEntity(saved));
    }

    // 17. Çözüm Notunu Güncelle (Sadece claim'li Agent — mevcut not zorunlu)
    @Operation(summary = "Çözüm notunu güncelle",
            description = "Bilete ait mevcut çözüm notunu günceller. Yalnızca bileti üzerine almış (claim'li) agent işlem yapabilir.")
    @PutMapping("/{id}/resolution-note")
    @PreAuthorize("hasRole('AGENT')")
    public ResponseEntity<ResolutionNoteResponseDTO> updateResolutionNote(
            @PathVariable Long id,
            @RequestBody ResolutionNoteRequestDTO dto,
            @AuthenticationPrincipal Jwt jwt) {
        String agentId = jwt.getSubject();
        log.info("Çözüm notu güncelleme isteği. Bilet ID: {}, Agent: {}", id, agentId);

        ResolutionNote saved = resolutionNoteService.updateResolutionNote(id, dto, agentId);

        log.info("Çözüm notu başarıyla güncellendi. Not ID: {}", saved.getId());
        return ResponseEntity.ok(ResolutionNoteResponseDTO.fromEntity(saved));
    }


    // 17. Biletin Çözüm Notunu Getir (Claim'li Agent kendi bileti / Manager hepsi)
    @Operation(summary = "Çözüm notunu getir",
            description = "Bir biletin çözüm notunu getirir. Agent yalnızca kendi üzerindeki biletin notunu görebilir.")
    @GetMapping("/{id}/resolution-note")
    @PreAuthorize("hasAnyRole('AGENT', 'MANAGER')")
    public ResponseEntity<ResolutionNoteResponseDTO> getResolutionNote(
            @PathVariable Long id,
            @AuthenticationPrincipal Jwt jwt) {
        String userId = jwt.getSubject();
        List<String> roles = JwtUtils.extractRoles(jwt);

        log.info("Çözüm notu görüntüleme isteği. Bilet ID: {}, Kullanıcı: {}", id, userId);

        ResolutionNote note = resolutionNoteService.getResolutionNoteByTicket(id, userId, roles);

        return ResponseEntity.ok(ResolutionNoteResponseDTO.fromEntity(note));
    }

    // 18. Tüm Çözüm Notlarını Listele (Sadece Manager)
    @Operation(summary = "Tüm çözüm notlarını listele",
            description = "Sistemdeki tüm biletlere ait çözüm notlarını getirir. Yalnızca Manager erişebilir.")
    @GetMapping("/all-resolution-notes")
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<List<ResolutionNoteResponseDTO>> getAllResolutionNotes() {
        log.info("Tüm çözüm notlarını listeleme isteği (Manager).");

        List<ResolutionNote> notes = resolutionNoteService.getAllResolutionNotes();

        log.info("Toplam {} çözüm notu listelendi.", notes.size());

        return ResponseEntity.ok(notes.stream()
                .map(ResolutionNoteResponseDTO::fromEntity)
                .collect(Collectors.toList()));
    }

    private TicketResponseDTO convertToDto(Ticket ticket) {
        return convertToDto(ticket, false);
    }

    private TicketResponseDTO convertToDto(Ticket ticket, boolean hasCsat) {
        String customerName = ticket.getCustomerId() != null 
            ? userRepository.findById(ticket.getCustomerId()).map(User::getFullName).orElse("Unknown") 
            : "Unknown";
        String assigneeName = ticket.getAssigneeId() != null 
            ? userRepository.findById(ticket.getAssigneeId()).map(User::getFullName).orElse(null) 
            : null;
        String productName = ticket.getProductId() != null 
            ? productRepository.findById(ticket.getProductId()).map(Product::getName).orElse("Unknown") 
            : "Unknown";
        return TicketResponseDTO.fromEntity(ticket, hasCsat, productName, customerName, assigneeName);
    }
}
