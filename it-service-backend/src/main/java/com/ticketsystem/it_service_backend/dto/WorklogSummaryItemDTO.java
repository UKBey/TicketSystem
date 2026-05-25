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
 * Worklog summary row for a single agent over a given period (total minutes, entry count, average).
 * Shown on manager reports as an element of {@link WorklogCompletionDTO#getAgentWorklogs()}.
 */
@Schema(description = "Agent bazında worklog özeti")
public class WorklogSummaryItemDTO {

    @Schema(description = "Agent kimliği", example = "a1b2c3d4-e5f6-7890-abcd-ef1234567890")
    private String agentId;

    @Schema(description = "Agent kullanıcı adı", example = "john.doe")
    private String agentUsername;

    @Schema(description = "Toplam kayıtlı çalışma (dakika)", example = "420")
    private long totalMinutes;

    @Schema(description = "Toplam worklog girişi", example = "12")
    private long totalEntries;

    @Schema(description = "Girş başına ortalama dakika", example = "35.0")
    private double avgMinutesPerEntry;
}
