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
@Schema(description = "Priority dağılımı metrikleri")
public class PriorityMetricsDTO {

    @Schema(description = "CRITICAL öncelikli açık bilet sayısı", example = "3")
    private Long critical;

    @Schema(description = "HIGH öncelikli açık bilet sayısı", example = "12")
    private Long high;

    @Schema(description = "MEDIUM öncelikli açık bilet sayısı", example = "85")
    private Long medium;

    @Schema(description = "LOW öncelikli açık bilet sayısı", example = "145")
    private Long low;
}
