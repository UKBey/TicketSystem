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
@Schema(description = "Priority bazlı SLA metrik detay satırı")
public class PriorityDetailDTO {

    @Schema(description = "Priority seviyesi", example = "CRITICAL")
    private String priority;

    @Schema(description = "Priority seviyesine ait toplam ticket sayısı", example = "3")
    private Long ticketCount;

    @Schema(description = "SLA hedef süresi (saat)", example = "4")
    private Integer slaTargetHours;

    @Schema(description = "Ortalama çözüm süresi (saat)", example = "2.1")
    private Double avgResolutionHours;

    @Schema(description = "SLA ihlali yapan ticket sayısı", example = "0")
    private Long breachCount;

    @Schema(description = "SLA ihlal yüzdesi", example = "0.0")
    private Double breachPercentage;

    @Schema(description = "SLA hedefini karşılayan ticket yüzdesi", example = "100.0")
    private Double onTimePercentage;
}
