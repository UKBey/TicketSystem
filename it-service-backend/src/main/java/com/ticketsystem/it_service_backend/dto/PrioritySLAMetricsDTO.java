package com.ticketsystem.it_service_backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Priority-SLA metrikleri yanıtı")
public class PrioritySLAMetricsDTO {

    @Schema(description = "Priority bazlı SLA metrik satırları")
    private List<PriorityDetailDTO> priorityMetrics;
}
