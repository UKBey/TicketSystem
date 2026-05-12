package com.ticketsystem.it_service_backend.controller;

import com.ticketsystem.it_service_backend.dto.AccessRequestDTO;
import com.ticketsystem.it_service_backend.service.AccessRequestService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
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

@Log4j2
@Tag(name = "Erişim Talepleri", description = "Rolsüz kullanıcıların erişim taleplerini yönetir")
@RestController
@RequestMapping("/api/access-requests")
@RequiredArgsConstructor
public class AccessRequestController {

    private final AccessRequestService accessRequestService;

    @Operation(summary = "Erişim talebi oluştur",
            description = "Rolsüz kullanıcı serbest metin ile erişim talebinde bulunur.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @PostMapping
    public ResponseEntity<AccessRequestDTO> createRequest(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody Map<String, String> body) {
        String message = body.get("message");
        if (message == null || message.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        String userId = jwt.getSubject();
        AccessRequestDTO dto = accessRequestService.createRequest(userId, message);
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    @Operation(summary = "Tüm erişim taleplerini listele (Admin)",
            description = "Tüm bekleyen erişim taleplerini en yeniden eskiye döner.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @GetMapping
    @PreAuthorize("hasRole('AGENT_ADMIN')")
    public ResponseEntity<List<AccessRequestDTO>> getAllRequests() {
        return ResponseEntity.ok(accessRequestService.getAllRequests());
    }

    @Operation(summary = "Kendi taleplerimi listele",
            description = "Giriş yapmış kullanıcının kendi erişim taleplerini döner.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @GetMapping("/my")
    public ResponseEntity<List<AccessRequestDTO>> getMyRequests(
            @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(accessRequestService.getRequestsByUser(jwt.getSubject()));
    }

    @Operation(summary = "Erişim talebini sil (Admin)",
            security = @SecurityRequirement(name = "bearerAuth"))
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('AGENT_ADMIN')")
    public ResponseEntity<Void> deleteRequest(@PathVariable Long id) {
        log.info("Erişim talebi silme isteği. ID: {}", id);
        accessRequestService.deleteRequest(id);
        return ResponseEntity.noContent().build();
    }
}
