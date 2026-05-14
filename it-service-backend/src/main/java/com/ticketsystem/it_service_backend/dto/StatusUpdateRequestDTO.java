package com.ticketsystem.it_service_backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Bilet durum güncelleme isteği. RESOLVED'a geçişte reasonCode zorunludur.")
public class StatusUpdateRequestDTO {

    @NotBlank(message = "{field.notblank}")
    @Schema(description = "Hedef durum", example = "RESOLVED", requiredMode = Schema.RequiredMode.REQUIRED)
    private String status;

    @Schema(description = "Sebep kodu (RESOLVED'a geçişte zorunlu)", example = "SOLUTION_PROVIDED")
    private String reasonCode;

    @Schema(description = "Serbest metin açıklama. reasonCode=OTHER ise zorunlu.", example = "Çözüm e-posta ile iletildi.")
    private String note;
}
