package com.ticketsystem.it_service_backend.controller;

import com.ticketsystem.it_service_backend.dto.CannedResponseDTO;
import com.ticketsystem.it_service_backend.service.CannedResponseService;
import com.ticketsystem.it_service_backend.util.JwtUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for canned responses (quick replies).
 *
 * <p>Every endpoint is restricted to {@code AGENT}, {@code LEAD_AGENT} and {@code ADMIN} —
 * customers can never reach the feature, so {@code INTERNAL} templates cannot leak to them.
 * Finer rules (own-personal vs. shared, ownership) live in {@link CannedResponseService}.
 */
@Log4j2
@Tag(name = "Hazır Yanıtlar", description = "Ajanların yorum yazarken kullandığı yeniden kullanılabilir metin şablonları")
@RestController
@RequestMapping("/api/v1/canned-responses")
@RequiredArgsConstructor
public class CannedResponseController {

    private final CannedResponseService service;

    /**
     * Lists the canned responses visible to the current agent. Optional filters:
     * {@code productId} (picker scope), {@code scope}, {@code visibility}, {@code q} (search).
     */
    @Operation(summary = "Görülebilir hazır yanıtları listele",
            description = "Kullanıcının kendi kişisel şablonları + tüm paylaşılan şablonlar. "
                    + "`productId` verilirse paylaşılanlar global + o ürüne ait olanlarla sınırlanır.")
    @GetMapping
    @PreAuthorize("hasAnyRole('AGENT', 'LEAD_AGENT', 'ADMIN')")
    public ResponseEntity<List<CannedResponseDTO>> list(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(name = "productId", required = false) Long productId,
            @RequestParam(name = "scope", required = false) String scope,
            @RequestParam(name = "visibility", required = false) String visibility,
            @RequestParam(name = "q", required = false) String q) {
        String userId = jwt.getSubject();
        return ResponseEntity.ok(service.listVisible(userId, productId, scope, visibility, q));
    }

    /**
     * Creates a canned response owned by the current agent. Creating a {@code SHARED}
     * template requires the admin/manager role (enforced in the service).
     */
    @Operation(summary = "Yeni hazır yanıt oluştur")
    @PostMapping
    @PreAuthorize("hasAnyRole('AGENT', 'LEAD_AGENT', 'ADMIN')")
    public ResponseEntity<CannedResponseDTO> create(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CannedResponseDTO body) {
        String userId = jwt.getSubject();
        List<String> roles = JwtUtils.extractRoles(jwt);
        log.info("Hazır yanıt oluşturma isteği. Başlık: {}, Kapsam: {}", body.getTitle(), body.getScope());
        return ResponseEntity.ok(service.create(body, userId, roles));
    }

    /**
     * Updates a canned response. The caller must own it (personal) or be admin/manager (shared).
     */
    @Operation(summary = "Hazır yanıtı güncelle")
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('AGENT', 'LEAD_AGENT', 'ADMIN')")
    public ResponseEntity<CannedResponseDTO> update(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long id,
            @Valid @RequestBody CannedResponseDTO body) {
        String userId = jwt.getSubject();
        List<String> roles = JwtUtils.extractRoles(jwt);
        log.info("Hazır yanıt güncelleme isteği. ID: {}", id);
        return ResponseEntity.ok(service.update(id, body, userId, roles));
    }

    /**
     * Deletes a canned response. The caller must own it (personal) or be admin/manager (shared).
     */
    @Operation(summary = "Hazır yanıtı sil")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('AGENT', 'LEAD_AGENT', 'ADMIN')")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long id) {
        String userId = jwt.getSubject();
        List<String> roles = JwtUtils.extractRoles(jwt);
        log.info("Hazır yanıt silme isteği. ID: {}", id);
        service.delete(id, userId, roles);
        return ResponseEntity.noContent().build();
    }

    /**
     * Marks a canned response as a favorite for the current user (idempotent).
     */
    @Operation(summary = "Hazır yanıtı favorile")
    @PostMapping("/{id}/favorite")
    @PreAuthorize("hasAnyRole('AGENT', 'LEAD_AGENT', 'ADMIN')")
    public ResponseEntity<Void> addFavorite(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long id) {
        service.addFavorite(id, jwt.getSubject());
        return ResponseEntity.noContent().build();
    }

    /**
     * Removes a canned response from the current user's favorites (idempotent).
     */
    @Operation(summary = "Hazır yanıt favorisini kaldır")
    @DeleteMapping("/{id}/favorite")
    @PreAuthorize("hasAnyRole('AGENT', 'LEAD_AGENT', 'ADMIN')")
    public ResponseEntity<Void> removeFavorite(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long id) {
        service.removeFavorite(id, jwt.getSubject());
        return ResponseEntity.noContent().build();
    }
}
