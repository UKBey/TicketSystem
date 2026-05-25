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
/**
 * Belirli bir zaman aralığındaki günlük ticket trendlerinin wrapper yanıtı.
 * Dashboard'un line chart'ı için {@link DailyMetricsDTO} listesini taşır.
 */
@Schema(description = "Ticket timeline — günlük trend metrikleri")
public class TicketTimelineDTO {

    @Schema(description = "Günlük metriklerin listesi", example = "[{\"date\": \"2026-04-01\", \"created\": 12, \"resolved\": 8, \"closed\": 3, \"slaBreach\": 1}]")
    private List<DailyMetricsDTO> timeline;
}
