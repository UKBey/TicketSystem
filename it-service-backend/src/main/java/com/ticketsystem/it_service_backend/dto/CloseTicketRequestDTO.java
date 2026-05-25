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
 * Bir bileti CLOSED durumuna geçirirken gönderilen istek.
 * Sebep kodu zorunludur (Bean Validation aktif); {@code reasonCode=OTHER} ise serbest metin de zorunlu olur.
 */
@Schema(description = "Close ticket isteği — sebep kodu (zorunlu) ve opsiyonel açıklama")
public class CloseTicketRequestDTO {

    @NotBlank(message = "{field.notblank}")
    @Schema(description = "Önceden tanımlı kapatma nedeni kodu", example = "RESOLVED_CONFIRMED", requiredMode = Schema.RequiredMode.REQUIRED)
    private String reasonCode;

    @Schema(description = "Serbest metin açıklama. reasonCode=OTHER ise zorunlu.", example = "Müşteri çağrıyla onay verdi.")
    private String note;
}
