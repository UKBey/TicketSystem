package com.ticketsystem.it_service_backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Agent-scoped CSAT summary for the selected period: headline average + response
 * count, the 1–5 rating distribution, and a daily average-rating trend. Powers the
 * CSAT card on the agent dashboard.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Ajanın seçili dönemdeki CSAT özeti — ortalama, dağılım ve trend")
public class AgentCsatDTO {

    @Schema(description = "Ortalama puan (0 = yanıt yok)", example = "4.4")
    private double average;

    @Schema(description = "Toplam yanıt sayısı", example = "52")
    private long totalResponses;

    @Schema(description = "Puan dağılımı (anahtar 1–5 → o puandaki yanıt sayısı)")
    private Map<Integer, Long> ratingDistribution;

    @Schema(description = "Günlük ortalama puan trendi")
    private List<CsatDailyDTO> trend;
}
