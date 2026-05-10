package com.ticketsystem.it_service_backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "SLA politikası güncelleme isteği")
public class SlaPolicyUpdateDTO {

    @NotNull(message = "{field.required}")
    @Min(value = 1, message = "{field.min}")
    @Schema(description = "Hedef çözüm süresi (saat)", example = "4")
    private Integer targetResolutionHours;

    @NotNull(message = "{field.required}")
    @Min(value = 0, message = "{field.min}")
    @Schema(description = "Uyarı eşiği (saat) — 0 ise uyarı gönderilmez", example = "2")
    private Integer warningThresholdHours;
}
