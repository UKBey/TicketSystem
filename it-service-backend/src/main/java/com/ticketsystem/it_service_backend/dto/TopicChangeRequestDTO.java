package com.ticketsystem.it_service_backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
/**
 * Bir biletin talep konusunu (topic) değiştirmek için gönderilen istek.
 * Yeni topic ID'si ve sebep kodu zorunludur (Bean Validation aktif); {@code reasonCode=OTHER} ise serbest metin de zorunlu olur.
 */
@Schema(description = "Bilet konusu güncelleme isteği — yeni topic, sebep kodu (zorunlu) ve opsiyonel açıklama")
public class TopicChangeRequestDTO {

    @NotNull(message = "{field.notblank}")
    @Schema(description = "Yeni topic ID", example = "12", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long topicId;

    @NotBlank(message = "{field.notblank}")
    @Schema(description = "Önceden tanımlı değişiklik nedeni kodu", example = "MISCATEGORIZED", requiredMode = Schema.RequiredMode.REQUIRED)
    private String reasonCode;

    @Schema(description = "Serbest metin açıklama. reasonCode=OTHER ise zorunlu.", example = "Aslında ağ değil sunucu konusu.")
    private String note;
}
