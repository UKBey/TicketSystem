package com.ticketsystem.it_service_backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
/**
 * Request model for adding or updating a worklog (time spent) on a ticket.
 * Carries minutes spent plus an optional description; consumed by agent endpoints.
 */
@Schema(description = "İş kaydı (worklog) oluşturma/güncelleme isteği")
public class WorklogRequestDTO {

    @Schema(description = "Harcanan süre (dakika cinsinden). Pozitif tam sayı olmalıdır.", example = "45", minimum = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer minutes;

    @Schema(description = "Yapılan işin açıklaması (opsiyonel, max 500 karakter)", example = "Firewall logları incelendi, port kuralları güncellendi.", maxLength = 500)
    private String description;
}
