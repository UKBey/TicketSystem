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
@Schema(description = "Agent performans satırı — dashboard leaderboard verisi")
public class AgentPerformanceItemDTO {

    @Schema(description = "Ajanın Keycloak ID'si", example = "f9e8d7c6-b5a4-3210-fedc-ba0987654321")
    private String agentId;

    @Schema(description = "Ajanın tam adı", example = "Mehmet Kaya")
    private String agentName;

    @Schema(description = "Uygulama rolü", example = "AGENT", allowableValues = {"AGENT", "AGENT_ADMIN"})
    private String role;

    @Schema(description = "Ajanın üzerindeki aktif ticket sayısı", example = "12")
    private Long activeTickets;

    @Schema(description = "Son 24 saatte çözülen ticket sayısı", example = "3")
    private Long resolvedLast24Hours;

    @Schema(description = "Ortalama çözüm süresi (saat)", example = "4.2")
    private Double avgResolutionHours;

    @Schema(description = "Ajanın ticket'ları için ortalama CSAT", example = "4.8")
    private Double csatAverage;

    @Schema(description = "SLA ihlali yapan ticket sayısı", example = "1")
    private Long slaBreachedCount;

    @Schema(description = "Son 7 günde girilen worklog süresi (dakika)", example = "480")
    private Long worklogMinutesLast7Days;
}