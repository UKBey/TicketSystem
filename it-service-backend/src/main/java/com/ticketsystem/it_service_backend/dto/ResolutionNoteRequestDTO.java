package com.ticketsystem.it_service_backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Çözüm notu oluşturma/güncelleme isteği")
public class ResolutionNoteRequestDTO {

    @Schema(description = "Çözüm açıklaması — sorunun nasıl çözüldüğünü detaylı açıklar. Boş olamaz.",
            example = "DNS ayarları düzeltildi, client tarafında cache temizlendi ve bağlantı doğrulandı.",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String note;
}
