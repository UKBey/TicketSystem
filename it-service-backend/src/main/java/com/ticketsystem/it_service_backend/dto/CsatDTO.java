package com.ticketsystem.it_service_backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
/**
 * CSAT (müşteri memnuniyeti) anketi gönderim isteği; çözülen bilete 1–5 puan ve opsiyonel yorum eklemek için kullanılır.
 */
@Schema(description = "Müşteri memnuniyet anketi (CSAT) isteği")
public class CsatDTO {
    
    @Schema(description = "Memnuniyet puanı (1 = çok kötü, 5 = mükemmel)", example = "4", minimum = "1", maximum = "5", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer rating;
    
    @Schema(description = "Müşterinin opsiyonel yorumu", example = "Sorun hızlıca çözüldü, teşekkürler!")
    private String comment;
}
