package com.ticketsystem.it_service_backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
/**
 * Bilete worklog (harcanan iş süresi) eklemek veya güncellemek için kullanılan istek modeli.
 * Dakika cinsinden süre ve opsiyonel açıklama içerir; agent endpoint'lerinde tüketilir.
 */
@Schema(description = "İş kaydı (worklog) oluşturma/güncelleme isteği")
public class WorklogRequestDTO {

    @Schema(description = "Harcanan süre (dakika cinsinden). Pozitif tam sayı olmalıdır.", example = "45", minimum = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer minutes;

    @Schema(description = "Yapılan işin açıklaması (opsiyonel, max 500 karakter)", example = "Firewall logları incelendi, port kuralları güncellendi.", maxLength = 500)
    private String description;
}
