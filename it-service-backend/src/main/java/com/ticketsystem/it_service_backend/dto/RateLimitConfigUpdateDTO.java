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
 * Request used by the admin panel to update an endpoint's rate-limit rule.
 * All fields are required (Bean Validation enforced); {@code enabled=false} acts as a kill switch.
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
