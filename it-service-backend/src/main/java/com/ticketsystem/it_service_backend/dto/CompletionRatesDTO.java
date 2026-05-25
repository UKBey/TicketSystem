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
 * Seçilen dönemde biletlerin tamamlanma oranları ile çözüm/SLA istatistikleri.
 * Manager raporlarında {@link WorklogCompletionDTO} içinde döndürülür.
 */
@Schema(description = "Bilet tamamlanma oranları ve çözüm istatistikleri")
public class CompletionRatesDTO {

    @Schema(description = "Dönem içinde çözülen bilet sayısı", example = "142")
    private long totalResolved;

    @Schema(description = "Dönem içinde kapatılan bilet sayısı", example = "130")
    private long totalClosed;

    @Schema(description = "Dönem içinde oluşturulan bilet sayısı", example = "165")
    private long totalCreated;

    @Schema(description = "Tamamlanma oranı: (resolved + closed) / created × 100", example = "83.6")
    private double completionRate;

    @Schema(description = "Ortalama çözüm süresi (saat)", example = "6.4")
    private double avgResolutionHours;

    @Schema(description = "SLA uyum oranı: çözülen biletlerde ihlal yaşanmayanların yüzdesi", example = "91.5")
    private double slaComplianceRate;
}
