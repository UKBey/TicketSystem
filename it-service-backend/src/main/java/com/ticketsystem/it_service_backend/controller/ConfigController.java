package com.ticketsystem.it_service_backend.controller;

import com.ticketsystem.it_service_backend.service.CommentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Exposes server-side configuration values the client needs to mirror, so the
 * frontend never hardcodes them. Single source of truth stays the backend's
 * {@code .env} / {@code application.yml} (e.g. {@code COMMENT_COOLDOWN_SECONDS}).
 */
@Tag(name = "İstemci Yapılandırması", description = "Frontend'in yansıtması gereken sunucu ayarları")
@RestController
@RequestMapping("/api/v1/config")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class ConfigController {

    private final CommentService commentService;

    /**
     * Comment-related client config: the per-user cooldown (seconds) and max length.
     * Driven by {@code app.comments.*} — the client reads this instead of hardcoding.
     *
     * @return {@code {"cooldownSeconds": <n>, "maxLength": <n>}}
     */
    @Operation(summary = "Yorum yapılandırması (cooldown + max uzunluk)")
    @GetMapping("/comments")
    public ResponseEntity<Map<String, Object>> commentConfig() {
        return ResponseEntity.ok(Map.of(
                "cooldownSeconds", commentService.getCooldownSeconds(),
                "maxLength", commentService.getMaxMessageLength()));
    }
}
