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
 * Tek bir öncelik seviyesi için CSAT özeti (ortalama puan ve yanıt sayısı).
 * {@link CSATMetricsDTO#getByPriority()} map'inin değerleri olarak kullanılır.
 */
@Schema(description = "Bir priority seviyesi için CSAT özeti")
public class CSATPriorityItemDTO {

    @Schema(description = "Bu priority için ortalama CSAT puanı", example = "4.50")
    private double avg;

    @Schema(description = "Bu priority için toplam CSAT yanıt sayısı", example = "24")
    private long responses;
}
