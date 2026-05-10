package com.ticketsystem.it_service_backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "SLA politikası güncelleme isteği")
public class SlaPolicyUpdateDTO {

    @NotNull
    @Min(value = 1, message = "Hedef çözüm süresi en az 1 saat olmalıdır.")
    @Schema(description = "Hedef çözüm süresi (saat)", example = "4")
    private Integer targetResolutionHours;

    @NotNull
    @Min(value = 0, message = "Uyarı eşiği 0 veya daha büyük olmalıdır.")
    @Schema(description = "Uyarı eşiği (saat) — 0 ise uyarı gönderilmez", example = "2")
    private Integer warningThresholdHours;
}
