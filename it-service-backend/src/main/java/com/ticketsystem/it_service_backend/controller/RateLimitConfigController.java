package com.ticketsystem.it_service_backend.controller;

import com.ticketsystem.it_service_backend.dto.RateLimitConfigResponseDTO;
import com.ticketsystem.it_service_backend.dto.RateLimitConfigUpdateDTO;
import com.ticketsystem.it_service_backend.entity.RateLimitConfig;
import com.ticketsystem.it_service_backend.interceptor.RateLimitInterceptor;
import com.ticketsystem.it_service_backend.service.RateLimitConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Admin-only REST endpoints for managing rate limit configurations.
 *
 * <p>All operations require the {@code AGENT_ADMIN} role.
 *
 * <p>PUT handler flow:
 * <ol>
 *   <li>Validate request body ({@code @Valid}).</li>
 *   <li>Persist new values via {@link RateLimitConfigService#updateConfig} --
 *       this also evicts the Caffeine cache for the affected endpointKey.</li>
 *   <li>Invalidate in-process Bucket4j buckets via
 *       {@link RateLimitInterceptor#invalidateBuckets} so the new limits
 *       take effect on the very next request.</li>
 *   <li>Return the updated config as a {@link RateLimitConfigResponseDTO}.</li>
 * </ol>
 */
@Log4j2
@Tag(name = "Rate Limit Yonetimi", description = "Admin: endpoint bazli hiz siniri konfigurasyonu")
@RestController
@RequestMapping("/api/admin/rate-limits")
@RequiredArgsConstructor
@PreAuthorize("hasRole('AGENT_ADMIN')")
public class RateLimitConfigController {

    private final RateLimitConfigService rateLimitConfigService;
    private final RateLimitInterceptor rateLimitInterceptor;

    /**
     * Returns all rate limit configurations.
     * Used by the admin panel to populate the configuration list.
     */
    @Operation(summary = "Tum rate limit konfigurasyonlarini listele")
    @GetMapping
    public ResponseEntity<List<RateLimitConfigResponseDTO>> getAllConfigs() {
        List<RateLimitConfigResponseDTO> configs = rateLimitConfigService.getAllConfigs()
                .stream()
                .map(RateLimitConfigResponseDTO::fromEntity)
                .toList();
        return ResponseEntity.ok(configs);
    }

    /**
     * Updates an existing rate limit configuration.
     *
     * <p>After persisting, the Caffeine cache entry and in-process Bucket4j buckets
     * for the affected endpoint are both invalidated, so the new limits are active
     * immediately without requiring an application restart.
     *
     * @param id  the primary key of the config to update
     * @param dto the new values (validated)
     * @return the updated configuration
     */
    @Operation(summary = "Rate limit konfigurasyonunu guncelle (cache + bucket invalidasyonu dahil)")
    @PutMapping("/{id}")
    public ResponseEntity<RateLimitConfigResponseDTO> updateConfig(
            @Parameter(description = "Guncellenecek konfigurasyon kaydi PK")
            @PathVariable Long id,
            @Valid @RequestBody RateLimitConfigUpdateDTO dto) {

        // 1. Persist + evict Caffeine cache
        RateLimitConfig updated = rateLimitConfigService.updateConfig(
                id,
                dto.getMaxRequests(),
                dto.getDurationSeconds(),
                dto.isEnabled()
        );

        // 2. Flush in-process Bucket4j buckets so new limits apply immediately
        rateLimitInterceptor.invalidateBuckets(updated.getEndpointKey());

        log.info("Rate limit config updated: id={} endpointKey={} maxRequests={} durationSeconds={} enabled={}",
                updated.getId(), updated.getEndpointKey(),
                updated.getMaxRequests(), updated.getDurationSeconds(), updated.isEnabled());

        return ResponseEntity.ok(RateLimitConfigResponseDTO.fromEntity(updated));
    }
}
