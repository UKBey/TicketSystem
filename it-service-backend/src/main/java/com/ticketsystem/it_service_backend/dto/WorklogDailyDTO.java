package com.ticketsystem.it_service_backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * One day of an agent's logged worklog effort — fuels the daily-worklog bar chart
 * on the agent dashboard. Days with no worklog are returned with {@code minutes = 0}
 * (the query gap-fills via {@code generate_series}).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Bir günün worklog dakikası (agent dashboard günlük worklog grafiği)")
public class WorklogDailyDTO {

    @Schema(description = "Gün", example = "2026-06-05")
    private LocalDate date;

    @Schema(description = "O gün girilen toplam worklog dakikası", example = "120")
    private long minutes;
}
