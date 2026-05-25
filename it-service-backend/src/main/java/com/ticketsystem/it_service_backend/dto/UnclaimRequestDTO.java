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
/**
 * Bir ajanın bilet claim'ini bırakırken (unclaim) gönderdiği istek.
 * Sebep kodu zorunludur (Bean Validation aktif); {@code reasonCode=OTHER} ise serbest metin de zorunlu olur.
 */
@Schema(description = "Unclaim (bırakma) isteği — sebep kodu (zorunlu) ve opsiyonel açıklama")
public class UnclaimRequestDTO {

    @NotBlank(message = "{field.notblank}")
    @Schema(description = "Önceden tanımlı bırakma nedeni kodu", example = "WORKLOAD", requiredMode = Schema.RequiredMode.REQUIRED)
    private String reasonCode;

    @Schema(description = "Serbest metin açıklama. reasonCode=OTHER ise zorunlu.", example = "Sistemde benzer 3 bilet daha açık.")
    private String note;
}
