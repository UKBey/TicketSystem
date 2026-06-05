package com.ticketsystem.it_service_backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * One day of an agent's CSAT — average rating and response count for the day.
 * Drives the average-rating trend line on the agent dashboard CSAT chart.
 * {@code avg} is null on days with no responses (so the trend line shows a gap).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Bir günün CSAT ortalaması ve yanıt sayısı (agent dashboard CSAT trendi)")
public class CsatDailyDTO {

    @Schema(description = "Gün", example = "2026-06-05")
    private LocalDate date;

    @Schema(description = "O günün ortalama puanı (yanıt yoksa null)", example = "4.5")
    private Double avg;

    @Schema(description = "O günün yanıt sayısı", example = "3")
    private Long count;
}
