package com.ticketsystem.it_service_backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
/**
 * Agent performance summary returned by the manager dashboard leaderboard endpoint.
 * Carries overall totals and the list of per-agent rows ({@link AgentPerformanceItemDTO}).
 */
@Schema(description = "Agent performans özeti — dashboard leaderboard response'u")
public class AgentPerformanceDTO {

    @Schema(description = "Listeleme anındaki UTC zamanı", example = "2026-05-01T11:30:00+03:00")
    private ZonedDateTime generatedAt;

    @Schema(description = "Performansı listelenen toplam ajan sayısı", example = "5")
    private Long totalAgents;

    @Schema(description = "Toplam aktif ticket sayısı", example = "47")
    private Long totalActiveTickets;

    @Schema(description = "Toplam SLA breach sayısı", example = "2")
    private Long totalSlaBreachedCount;

    @Schema(description = "Toplam son 24 saat çözüm sayısı", example = "18")
    private Long totalResolvedLast24Hours;

    @Schema(description = "Genel CSAT ortalaması", example = "4.6")
    private Double averageCsat;

    @Schema(description = "Ajan performans satırları")
    private List<AgentPerformanceItemDTO> agents;
}