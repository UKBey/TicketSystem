package com.ticketsystem.it_service_backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "CSAT detaylı analitik metrikleri")
public class CSATMetricsDTO {

    @Schema(description = "Toplam CSAT yanıt sayısı (seçilen zaman diliminde)", example = "152")
    private long totalResponses;

    @Schema(description = "Genel CSAT puan ortalaması (1–5)", example = "4.42")
    private double averageRating;

    @Schema(description = "Puana göre yanıt dağılımı (anahtar=puan 1-5, değer=yanıt sayısı)")
    private Map<Integer, Long> ratingDistribution;

    @Schema(description = "Bu ay ile geçen ay CSAT karşılaştırması")
    private CSATTrendDTO trend;

    @Schema(description = "Priority seviyesine göre CSAT ortalamaları")
    private Map<String, CSATPriorityItemDTO> byPriority;

    @Schema(description = "Son dönemden en yüksek puanlı yorumlar (en fazla 5 adet)")
    private List<String> topComments;
}
