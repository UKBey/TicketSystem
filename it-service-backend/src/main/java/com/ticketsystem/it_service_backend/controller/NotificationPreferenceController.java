package com.ticketsystem.it_service_backend.controller;

import com.ticketsystem.it_service_backend.dto.NotificationPreferenceResponse;
import com.ticketsystem.it_service_backend.dto.UpdateNotificationPreferenceRequest;
import com.ticketsystem.it_service_backend.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
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

@Log4j2
@Tag(name = "Bildirim Tercihleri", description = "Kullanıcının e-posta bildirim tercihlerini yönetme işlemleri")
@RestController
@RequestMapping("/api/notification-preferences")
@RequiredArgsConstructor
// S-6: defense-in-depth — kullanici kendi tercihlerini okur/yazar; class-level
// isAuthenticated SecurityConfig'in JWT zorunlulugunu kodda gorunur kilar.
@PreAuthorize("isAuthenticated()")
public class NotificationPreferenceController {

    private final NotificationService notificationService;

    @Operation(summary = "Bildirim tercihlerini getir",
            description = "Kullanıcının mevcut bildirim tercihlerini döner. " +
                    "Tercih satırı yoksa tüm alanlar varsayılan olarak true döner.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Tercihler başarıyla döndü")
    })
    @GetMapping
    public ResponseEntity<NotificationPreferenceResponse> getPreferences(
            @AuthenticationPrincipal Jwt jwt) {

        String userId = jwt.getSubject();
        log.info("Bildirim tercihleri getirme isteği. Kullanıcı: {}", userId);
        return ResponseEntity.ok(notificationService.getPreferences(userId));
    }

    @Operation(summary = "Bildirim tercihlerini güncelle",
            description = "Kullanıcının e-posta bildirim tercihlerini günceller. " +
                    "Gönderilmeyen (null) alanlar mevcut değerleriyle kalır.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Tercihler başarıyla güncellendi")
    })
    @PutMapping
    public ResponseEntity<NotificationPreferenceResponse> updatePreferences(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody UpdateNotificationPreferenceRequest request) {

        String userId = jwt.getSubject();
        log.info("Bildirim tercihleri güncelleme isteği. Kullanıcı: {}", userId);
        return ResponseEntity.ok(notificationService.updatePreferences(userId, request));
    }
}
