package com.ticketsystem.it_service_backend.controller;

import com.ticketsystem.it_service_backend.dto.NotificationResponse;
import com.ticketsystem.it_service_backend.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Log4j2
@Tag(name = "Bildirim Yönetimi", description = "Kullanıcı bildirimleri ve okundu işaretleme işlemleri")
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @Operation(summary = "Bildirimleri listele", description = "Oturum açmış kullanıcının bildirimlerini sayfalı şekilde getirir.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Bildirimler başarıyla listelendi")
    })
    @GetMapping
    public ResponseEntity<Page<NotificationResponse>> getNotifications(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        String userId = jwt.getSubject();
        log.info("Bildirim listeleme isteği. Kullanıcı: {}, Sayfa: {}", userId, page);

        Page<NotificationResponse> result = notificationService.getNotificationsForUser(
                userId, PageRequest.of(page, size, Sort.by("createdAt").descending()));

        return ResponseEntity.ok(result);
    }

    @Operation(summary = "Okunmamış bildirim sayısı", description = "Kullanıcının okunmamış bildirim sayısını döner.")
    @ApiResponse(responseCode = "200", description = "Sayı başarıyla döndü")
    @GetMapping("/unread-count")
    public ResponseEntity<Map<String, Long>> getUnreadCount(@AuthenticationPrincipal Jwt jwt) {
        String userId = jwt.getSubject();
        long count = notificationService.getUnreadCount(userId);
        return ResponseEntity.ok(Map.of("count", count));
    }

    @Operation(summary = "Bildirimi okundu işaretle", description = "Belirtilen bildirimi okundu olarak işaretler.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Bildirim okundu işaretlendi"),
            @ApiResponse(responseCode = "403", description = "Bu bildirim size ait değil"),
            @ApiResponse(responseCode = "404", description = "Bildirim bulunamadı")
    })
    @PatchMapping("/{id}/read")
    public ResponseEntity<Void> markAsRead(
            @PathVariable Long id,
            @AuthenticationPrincipal Jwt jwt) {

        String userId = jwt.getSubject();
        log.info("Bildirim okundu işaretleme isteği. Bildirim ID: {}, Kullanıcı: {}", id, userId);
        notificationService.markAsRead(id, userId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Tüm bildirimleri okundu işaretle", description = "Kullanıcının tüm bildirimlerini okundu olarak işaretler.")
    @ApiResponse(responseCode = "204", description = "Tüm bildirimler okundu işaretlendi")
    @PostMapping("/read-all")
    public ResponseEntity<Void> markAllAsRead(@AuthenticationPrincipal Jwt jwt) {
        String userId = jwt.getSubject();
        log.info("Tüm bildirimleri okundu işaretleme isteği. Kullanıcı: {}", userId);
        notificationService.markAllAsRead(userId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Bildirimi sil", description = "Belirtilen bildirimi kalıcı olarak siler. Yalnızca bildirimin sahibi silebilir.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Bildirim silindi"),
            @ApiResponse(responseCode = "404", description = "Bildirim bulunamadı veya size ait değil")
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteNotification(
            @PathVariable Long id,
            @AuthenticationPrincipal Jwt jwt) {
        String userId = jwt.getSubject();
        log.info("Bildirim silme isteği. Bildirim ID: {}, Kullanıcı: {}", id, userId);
        notificationService.deleteNotification(id, userId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Tüm bildirimleri sil", description = "Kullanıcının tüm bildirimlerini kalıcı olarak siler.")
    @ApiResponse(responseCode = "204", description = "Tüm bildirimler silindi")
    @DeleteMapping
    public ResponseEntity<Void> deleteAllNotifications(@AuthenticationPrincipal Jwt jwt) {
        String userId = jwt.getSubject();
        log.info("Tüm bildirimleri silme isteği. Kullanıcı: {}", userId);
        notificationService.deleteAllNotifications(userId);
        return ResponseEntity.noContent().build();
    }
}
