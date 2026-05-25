package com.ticketsystem.it_service_backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
/**
 * Admin panelinden bir endpoint'in rate limit kuralini guncellemek icin kullanilan istek.
 * Tum alanlar zorunludur (Bean Validation aktif); {@code enabled=false} kill switch gorevi gorur.
 */
@Schema(description = "Rate limit konfigurasyonu guncelleme istegi")
public class RateLimitConfigUpdateDTO {

    @Min(value = 1, message = "{field.min}")
    @Schema(description = "Zaman penceresi icinde izin verilen maksimum istek sayisi",
            example = "10",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private int maxRequests;

    @Min(value = 1, message = "{field.min}")
    @Schema(description = "Token bucket yenileme penceresi (saniye cinsinden)",
            example = "60",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private int durationSeconds;

    @Schema(description = "false ise bu endpoint icin rate limiting devre disi birakilir (kill switch)",
            example = "true",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private boolean enabled;
}
