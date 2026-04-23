package com.ticketsystem.it_service_backend.controller;

import com.ticketsystem.it_service_backend.dto.ResolutionNoteRequestDTO;
import com.ticketsystem.it_service_backend.dto.ResolutionNoteResponseDTO;
import com.ticketsystem.it_service_backend.entity.ResolutionNote;
import com.ticketsystem.it_service_backend.service.ResolutionNoteService;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

@Log4j2
@Tag(name = "Ticket Resolution Note", description = "Bilet çözüldüğünde yazılması zorunlu olan çözüm notları")
@RestController
@RequestMapping("/api/tickets")
@RequiredArgsConstructor
public class TicketResolutionNoteController {

    private final ResolutionNoteService resolutionNoteService;

    @Operation(summary = "Çözüm notu oluştur",
            description = """
                    Bilete ilk çözüm notunu ekler. Her bilet için **en fazla bir** çözüm notu olabilir.
                    
                    **Kurallar:**
                    - Yalnızca bileti üzerine almış (claim'li) agent işlem yapabilir
                    - Bilete daha önce not eklenmiş olmamalı (varsa `PUT` ile güncellenir)
                    - Bileti `RESOLVED` statüsüne geçirmek için **önce** bu endpoint çağrılmalıdır
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Çözüm notu başarıyla oluşturuldu",
                    content = @Content(schema = @Schema(implementation = ResolutionNoteResponseDTO.class))),
            @ApiResponse(responseCode = "400", description = "Bu bilet için zaten bir çözüm notu mevcut"),
            @ApiResponse(responseCode = "403", description = "Sadece bileti sahiplenmiş agent çözüm notu oluşturabilir")
    })
    @PostMapping("/{id}/resolution-note")
    @PreAuthorize("hasRole('AGENT')")
    public ResponseEntity<ResolutionNoteResponseDTO> createResolutionNote(
            @Parameter(description = "Biletin ID'si", example = "42", required = true)
            @PathVariable Long id,
            @RequestBody ResolutionNoteRequestDTO dto,
            @AuthenticationPrincipal Jwt jwt) {
        String agentId = jwt.getSubject();
        log.info("Çözüm notu oluşturma isteği. Bilet ID: {}, Agent: {}", id, agentId);

        ResolutionNote saved = resolutionNoteService.createResolutionNote(id, dto, agentId);

        log.info("Çözüm notu başarıyla oluşturuldu. Not ID: {}", saved.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(ResolutionNoteResponseDTO.fromEntity(saved));
    }

    @Operation(summary = "Çözüm notunu güncelle",
            description = "Bilete ait mevcut çözüm notunun içeriğini günceller. "
                    + "Genellikle bilet tekrar RESOLVED yapılmadan önce kullanılır.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Çözüm notu başarıyla güncellendi",
                    content = @Content(schema = @Schema(implementation = ResolutionNoteResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "Bu bilet için çözüm notu bulunamadı"),
            @ApiResponse(responseCode = "403", description = "Sadece bileti sahiplenmiş agent güncelleyebilir")
    })
    @PutMapping("/{id}/resolution-note")
    @PreAuthorize("hasRole('AGENT')")
    public ResponseEntity<ResolutionNoteResponseDTO> updateResolutionNote(
            @Parameter(description = "Biletin ID'si", example = "42", required = true)
            @PathVariable Long id,
            @RequestBody ResolutionNoteRequestDTO dto,
            @AuthenticationPrincipal Jwt jwt) {
        String agentId = jwt.getSubject();
        log.info("Çözüm notu güncelleme isteği. Bilet ID: {}, Agent: {}", id, agentId);

        ResolutionNote saved = resolutionNoteService.updateResolutionNote(id, dto, agentId);

        log.info("Çözüm notu başarıyla güncellendi. Not ID: {}", saved.getId());
        return ResponseEntity.ok(ResolutionNoteResponseDTO.fromEntity(saved));
    }

    @Operation(summary = "Çözüm notunu getir",
            description = "Belirtilen biletin çözüm notunu getirir. Agent yalnızca kendi üzerindeki biletin notunu görebilir, Manager tüm biletleri görebilir.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Çözüm notu başarıyla döndü",
                    content = @Content(schema = @Schema(implementation = ResolutionNoteResponseDTO.class))),
            @ApiResponse(responseCode = "403", description = "Bu biletin çözüm notunu görüntüleme yetkiniz yok"),
            @ApiResponse(responseCode = "404", description = "Çözüm notu bulunamadı")
    })
    @GetMapping("/{id}/resolution-note")
    @PreAuthorize("hasAnyRole('AGENT', 'MANAGER')")
    public ResponseEntity<ResolutionNoteResponseDTO> getResolutionNote(
            @Parameter(description = "Biletin ID'si", example = "42", required = true)
            @PathVariable Long id,
            @AuthenticationPrincipal Jwt jwt) {
        String userId = jwt.getSubject();
        List<String> roles = JwtUtils.extractRoles(jwt);

        log.info("Çözüm notu görüntüleme isteği. Bilet ID: {}, Kullanıcı: {}", id, userId);

        ResolutionNote note = resolutionNoteService.getResolutionNoteByTicket(id, userId, roles);

        return ResponseEntity.ok(ResolutionNoteResponseDTO.fromEntity(note));
    }

    @Operation(summary = "Tüm çözüm notlarını listele",
            description = "Sistemdeki tüm biletlere ait çözüm notlarını getirir. Raporlama ve denetim amaçlıdır.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Tüm çözüm notları başarıyla listelendi"),
            @ApiResponse(responseCode = "403", description = "Yalnızca MANAGER erişebilir")
    })
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
}
