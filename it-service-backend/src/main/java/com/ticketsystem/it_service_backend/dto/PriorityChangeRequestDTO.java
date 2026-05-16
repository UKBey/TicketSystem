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
@Schema(description = "Bilet önceliği güncelleme isteği — yeni öncelik, sebep kodu (zorunlu) ve opsiyonel açıklama")
public class PriorityChangeRequestDTO {

    @NotBlank(message = "{field.notblank}")
    @Schema(description = "Yeni öncelik", example = "HIGH", requiredMode = Schema.RequiredMode.REQUIRED)
    private String priority;

    @NotBlank(message = "{field.notblank}")
    @Schema(description = "Önceden tanımlı değişiklik nedeni kodu", example = "CUSTOMER_IMPACT", requiredMode = Schema.RequiredMode.REQUIRED)
    private String reasonCode;

    @Schema(description = "Serbest metin açıklama. reasonCode=OTHER ise zorunlu.", example = "Yöneticinin talebi üzerine yükseltildi.")
    private String note;
}
