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
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
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
@Tag(name = "Bilet Yönetimi", description = "Destek biletlerinin oluşturulması, listelenmesi, sahiplenilmesi, durum güncellenmesi ve silinmesi")
@RestController
@RequestMapping("/api/tickets")
@RequiredArgsConstructor
public class TicketController {

    private final TicketService ticketService;
    private final UserRepository userRepository;
    private final ProductRepository productRepository;

    // Bilet yasam dongusu islemleri.

    // Musteri tarafindan yeni destek kaydi olusturur.
    @Operation(summary = "Yeni bilet oluştur",
            description = "Müşteri rolündeki kullanıcı, yetkili olduğu bir ürün altında yeni destek bileti açar. "
                    + "Biletin açıklama metni otomatik olarak ilk yorum (EXTERNAL) olarak da kaydedilir. "
                    + "Oluşturma sonrası jBPM workflow başlatılarak SLA sayacı devreye girer.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Bilet başarıyla oluşturuldu",
                    content = @Content(schema = @Schema(implementation = TicketResponseDTO.class))),
            @ApiResponse(responseCode = "403", description = "Kullanıcı bu ürün için yetkili değil"),
            @ApiResponse(responseCode = "401", description = "Geçersiz veya eksik JWT token")
    })
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
    @Operation(summary = "Biletleri listele",
            description = "Kullanıcının rolüne göre erişebileceği biletleri döner:\n"
                    + "- **CUSTOMER**: Yalnızca kendi oluşturduğu biletler\n"
                    + "- **AGENT**: Kendi biletleri + yetkili olduğu ürün grubundaki biletler\n"
                    + "- **MANAGER**: Sistemdeki tüm biletler\n\n"
                    + "Her bilet SLA gerçek zamanlı bilgisiyle birlikte döner.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Bilet listesi başarıyla döndü"),
            @ApiResponse(responseCode = "401", description = "Geçersiz veya eksik JWT token")
    })
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
    @Operation(summary = "Havuzdaki biletleri listele",
            description = "Henüz bir ajana atanmamış (`NEW` statüsündeki) biletleri getirir. "
                    + "Agent yalnızca yetkili olduğu ürün grubundaki havuz biletlerini görür; Manager tüm havuzu görür.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Havuz biletleri başarıyla listelendi"),
            @ApiResponse(responseCode = "401", description = "Geçersiz veya eksik JWT token"),
            @ApiResponse(responseCode = "403", description = "CUSTOMER rolü havuza erişemez")
    })
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
    @Operation(summary = "Ajanın üzerindeki biletleri listele",
            description = "Giriş yapan ajana (assigneeId) atanmış tüm aktif biletleri getirir.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Atanan biletler başarıyla listelendi"),
            @ApiResponse(responseCode = "401", description = "Geçersiz veya eksik JWT token")
    })
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
    @Operation(summary = "Bilet detayı getir",
            description = "Belirtilen ID'ye sahip biletin tüm detaylarını getirir. Erişim yetki kontrolüne tabidir:\n"
                    + "- **CUSTOMER**: Yalnızca kendi bileti\n"
                    + "- **AGENT**: Yetkili olduğu ürün grubundaki biletler\n"
                    + "- **MANAGER**: Tüm biletler")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Bilet detayı başarıyla döndü",
                    content = @Content(schema = @Schema(implementation = TicketResponseDTO.class))),
            @ApiResponse(responseCode = "403", description = "Bu bileti görüntüleme yetkiniz yok"),
            @ApiResponse(responseCode = "404", description = "Bilet bulunamadı")
    })
    @GetMapping("/{id}")
    public ResponseEntity<TicketResponseDTO> getTicket(
            @Parameter(description = "Biletin benzersiz ID'si", example = "42", required = true)
            @PathVariable Long id,
            @AuthenticationPrincipal Jwt jwt) {
        String userId = jwt.getSubject();
        List<String> roles = JwtUtils.extractRoles(jwt);

        log.info("Bilet detayı isteği. Bilet ID: {}, Kullanıcı: {}", id, userId);

        Ticket ticket = ticketService.getTicketWithAuth(id, userId, roles);
        log.info("Bilet detayı başarıyla çekildi. Bilet ID: {}", id);

        return ResponseEntity.ok(convertToDto(ticket, false, roles));
    }

    // Agentin havuzdaki bileti sahiplenmesini saglar.
    @Operation(summary = "Bileti sahiplen (Claim)",
            description = "Havuzdaki (`NEW` statüsündeki) bir bileti ajanın kendi üzerine almasını sağlar. "
                    + "Bilet `IN_PROGRESS` statüsüne geçer ve `assigneeId` güncellenir. "
                    + "Agent yalnızca yetkili olduğu ürün grubundaki biletleri sahiplenebilir. "
                    + "jBPM workflow'unda atama bilgisi de senkronize edilir.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Bilet başarıyla sahiplenildi",
                    content = @Content(schema = @Schema(implementation = TicketResponseDTO.class))),
            @ApiResponse(responseCode = "403", description = "Bu ürüne ait biletleri üzerinize alma yetkiniz yok"),
            @ApiResponse(responseCode = "400", description = "Bilet NEW statüsünde değil, sahiplenilemez")
    })
    @PutMapping("/{id}/claim")
    @PreAuthorize("hasRole('AGENT')")
    public ResponseEntity<TicketResponseDTO> claimTicket(
            @Parameter(description = "Sahiplenilecek biletin ID'si", example = "42", required = true)
            @PathVariable Long id,
            @AuthenticationPrincipal Jwt jwt) {
        String agentId = jwt.getSubject();

        log.info("Bileti sahiplenme (claim) isteği. Bilet ID: {}, Ajan ID: {}", id, agentId);

        Ticket ticket = ticketService.claimTicket(id, agentId);

        log.info("Bilet başarıyla sahiplenildi. Bilet ID: {}, Ajan ID: {}", id, agentId);

        return ResponseEntity.ok(convertToDto(ticket, false, List.of("AGENT")));
    }

    // Durum makinesi kurallarina uygun statu degisimi yapar.
    @Operation(summary = "Bilet statüsü güncelle",
            description = """
                    Biletin durumunu günceller. Geçiş kuralları (state machine):
                    
                    | Mevcut Durum           | İzin Verilen Hedefler                                    |
                    |------------------------|----------------------------------------------------------|
                    | NEW                    | IN_PROGRESS                                              |
                    | IN_PROGRESS            | NEW, WAITING_FOR_CUSTOMER, RESOLVED, CLOSED              |
                    | WAITING_FOR_CUSTOMER   | IN_PROGRESS                                              |
                    | RESOLVED               | IN_PROGRESS, CLOSED                                      |
                    | CLOSED                 | _(son durum — geçiş yapılamaz)_                          |
                    
                    **Önemli kurallar:**
                    - `RESOLVED` geçişi için **çözüm notu zorunludur** (önce `/resolution-note` endpoint'i kullanılmalı)
                    - `IN_PROGRESS → NEW` geçişi bırakma (unclaim) anlamına gelir, `assigneeId` sıfırlanır
                    - Müşteri yalnızca `WAITING_FOR_CUSTOMER → IN_PROGRESS` ve `RESOLVED → IN_PROGRESS/CLOSED` geçişlerini yapabilir
                    - SLA sayacı durum değişimine göre otomatik olarak duraklatılır/devam ettirilir
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Statü başarıyla güncellendi",
                    content = @Content(schema = @Schema(implementation = TicketResponseDTO.class))),
            @ApiResponse(responseCode = "400", description = "Geçersiz durum geçişi veya çözüm notu eksik"),
            @ApiResponse(responseCode = "403", description = "Bu durum geçişi için yetkiniz yok")
    })
    @PutMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'AGENT', 'MANAGER')")
    public ResponseEntity<TicketResponseDTO> updateStatus(
            @Parameter(description = "Biletin ID'si", example = "42", required = true)
            @PathVariable Long id,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Yeni statü bilgisi. `status` anahtarı ile gönderilir.",
                    content = @Content(schema = @Schema(example = "{\"status\": \"RESOLVED\"}")))
            @RequestBody Map<String, String> body,
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
    @Operation(summary = "Bileti sil",
            description = "Bileti ve tüm bağlı verilerini (yorumlar, dosyalar, worklog, çözüm notu, CSAT) kalıcı olarak siler. "
                    + "İlişkili jBPM workflow'u da sonlandırılır. **Bu işlem geri alınamaz.**")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Bilet ve bağlı kayıtları başarıyla silindi"),
            @ApiResponse(responseCode = "403", description = "Sadece MANAGER rolü bilet silebilir"),
            @ApiResponse(responseCode = "404", description = "Bilet bulunamadı")
    })
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<Void> deleteTicket(
            @Parameter(description = "Silinecek biletin ID'si", example = "42", required = true)
            @PathVariable Long id) {
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
        dto.setSlaInfo(ticketService.getSlaTimerInfo(ticket));
        return dto;
    }

    @Operation(summary = "SLA zamanlayıcı bilgisi",
            description = """
                    jBPM workflow'undan biletin gerçek zamanlı SLA geri sayım bilgisini döner.
                    
                    Dönen alanlar:
                    - **deadlineTs**: SLA son tarihinin Unix timestamp değeri (ms). Sayaç aktifken kullanılır.
                    - **remainingMs**: Sayaç duraklatıldıysa kalan süre (ms).
                    - **breached**: 1 ise SLA ihlal edilmiş demektir.
                    
                    SLA sayacı `WAITING_FOR_CUSTOMER` ve `RESOLVED` durumlarında otomatik duraklatılır.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "SLA bilgisi başarıyla döndü"),
            @ApiResponse(responseCode = "403", description = "Bu bilete erişim yetkiniz yok"),
            @ApiResponse(responseCode = "404", description = "Bilet bulunamadı")
    })
    @GetMapping("/{id}/sla-timer")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'AGENT', 'MANAGER')")
    public ResponseEntity<Map<String, Long>> getSlaTimer(
            @Parameter(description = "Biletin ID'si", example = "42", required = true)
            @PathVariable Long id,
            @AuthenticationPrincipal Jwt jwt) {
        String userId = jwt.getSubject();
        List<String> roles = JwtUtils.extractRoles(jwt);
        Ticket ticket = ticketService.getTicketWithAuth(id, userId, roles);
        Map<String, Long> response = ticketService.getSlaTimerInfo(ticket);
        return ResponseEntity.ok(response);
    }

}