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
 * Manager dashboard'un üst KPI kartlarını besleyen özet metrik yanıtı.
 * Açık bilet sayıları, SLA ihlal oranı, ortalama yanıt süresi ve öncelik dağılımı içerir.
 */
@Schema(description = "Dashboard özet metrikleri — KPI kartları için temel veriler")
public class DashboardMetricsDTO {

    @Schema(description = "Açık biletlerin toplam sayısı (NEW, IN_PROGRESS, WAITING_FOR_CUSTOMER)", example = "245")
    private Long totalOpenTickets;

    @Schema(description = "Son 24 saatte oluşan yeni bilet sayısı", example = "15")
    private Long newTicketsLast24Hours;

    @Schema(description = "SLA'yı aşan biletlerin sayısı", example = "12")
    private Long slaBreachedCount;

    @Schema(description = "SLA breach yüzdesi", example = "4.9")
    private Double slaBreachedPercentage;

    @Schema(description = "Ortalama yanıt süresi (saat cinsinden)", example = "3.2")
    private Double avgResponseTimeHours;

    @Schema(description = "CSAT puanı ortalaması (1-5)", example = "4.6")
    private Double csatAverage;

    @Schema(description = "CSAT anketi cevap sayısı", example = "152")
    private Long csatTotalResponses;

    @Schema(description = "Prioritye göre bilet dağılımı", example = "{\"CRITICAL\": 3, \"HIGH\": 12, \"MEDIUM\": 85, \"LOW\": 145}")
    private PriorityMetricsDTO priorityDistribution;
}
