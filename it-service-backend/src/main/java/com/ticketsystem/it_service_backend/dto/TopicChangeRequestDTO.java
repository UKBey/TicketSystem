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
 * Request to change a ticket's topic.
 * The new topic ID and a reason code are required (Bean Validation enforced); when {@code reasonCode=OTHER} a free-text note is required too.
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
