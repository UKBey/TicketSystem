package com.ticketsystem.it_service_backend.dto;

import com.ticketsystem.it_service_backend.entity.RateLimitConfig;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
/**
 * Response model for the rate-limit rule configured on an endpoint.
 * Used by the admin panel to display Bucket4j settings.
 */
@Schema(description = "Rate limit konfigurasyonu yaniti")
public class RateLimitConfigResponseDTO {

    @Schema(description = "Konfigurasyon kaydi icin birincil anahtar", example = "1")
    private Long id;

    @Schema(description = "Endpoint mantiksal anahtari", example = "CLAIM_TICKET")
    private String endpointKey;

    @Schema(description = "Admin panelinde gosterilecek aciklama",
            example = "Bilet claim limiti (saniyede)")
    private String description;

    @Schema(description = "Zaman penceresi icinde izin verilen maksimum istek sayisi",
            example = "10")
    private int maxRequests;

    @Schema(description = "Token bucket yenileme penceresi (saniye cinsinden)", example = "60")
    private int durationSeconds;

    @Schema(description = "Rate limiting etkin mi (kill switch)", example = "true")
    private boolean enabled;

    @Schema(description = "Son guncelleme zamani (UTC)")
    private OffsetDateTime updatedAt;

    /**
     * Converts a {@link RateLimitConfig} entity to its response DTO.
     */
    public static RateLimitConfigResponseDTO fromEntity(RateLimitConfig entity) {
        return RateLimitConfigResponseDTO.builder()
                .id(entity.getId())
                .endpointKey(entity.getEndpointKey())
                .description(entity.getDescription())
                .maxRequests(entity.getMaxRequests())
                .durationSeconds(entity.getDurationSeconds())
                .enabled(entity.isEnabled())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
