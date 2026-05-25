package com.ticketsystem.it_service_backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
/**
 * Tek bir güne ait ticket metrikleri (oluşturulan, çözülen, kapatılan, SLA breach).
 * Dashboard timeline chart'ında {@link TicketTimelineDTO} listesinin bir elemanı olarak döner.
 */
@Schema(description = "Günlük ticket metrikleri — timeline chart verisi")
public class DailyMetricsDTO {

    @Schema(description = "Metrik tarihi (YYYY-MM-DD)", example = "2026-04-15")
    private LocalDate date;

    @Schema(description = "Bu tarihte oluşturulan bilet sayısı", example = "12")
    private Long created;

    @Schema(description = "Bu tarihte çözülen bilet sayısı", example = "8")
    private Long resolved;

    @Schema(description = "Bu tarihte kapalı bilet sayısı", example = "3")
    private Long closed;

    @Schema(description = "Bu tarihte SLA ihlali olan bilet sayısı", example = "1")
    private Long slaBreach;
}
