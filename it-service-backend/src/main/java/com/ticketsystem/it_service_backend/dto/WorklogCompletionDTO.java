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
@Schema(description = "Worklog özeti ve bilet tamamlanma metrikleri")
public class WorklogCompletionDTO {

    @Schema(description = "Analiz edilen gün sayısı", example = "30")
    private int periodDays;

    @Schema(description = "Agent bazında worklog özetleri (toplam dakikaya göre azalan)")
    private List<WorklogSummaryItemDTO> agentWorklogs;

    @Schema(description = "Dönem bilet tamamlanma istatistikleri")
    private CompletionRatesDTO completionRates;
}
