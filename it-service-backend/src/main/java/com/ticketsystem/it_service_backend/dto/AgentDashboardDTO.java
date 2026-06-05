package com.ticketsystem.it_service_backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Personal dashboard for an AGENT / LEAD_AGENT — aggregates strictly over tickets
 * the user worked, i.e. holds a claim on ({@code ticket_claims.agent_id = userId})
 * plus their own worklogs. Independent of any tickets the same user opened as a
 * customer (those live in {@code tickets.customer_id}).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Ajanın kişisel performans dashboard'u — yalnızca claim'lediği biletler üzerinden")
public class AgentDashboardDTO {

    @Schema(description = "Claim'lediğim açık biletler (NEW/IN_PROGRESS/WAITING_FOR_CUSTOMER)", example = "7")
    private Long activeTickets;

    @Schema(description = "Claim'lediğim toplam bilet sayısı", example = "120")
    private Long totalClaimed;

    @Schema(description = "Seçili dönemde çözdüğüm bilet sayısı", example = "18")
    private Long resolvedInRange;

    @Schema(description = "Seçili dönemde çözdüğüm biletlerden SLA ihlali olanların sayısı", example = "4")
    private Long slaBreachedCount;

    @Schema(description = "SLA ihlal oranım (% — dönemde ihlalli / dönemde çözülen)", example = "3.3")
    private Double slaBreachRate;

    @Schema(description = "Seçili dönemde çözdüğüm biletlerin ortalama çözüm süresi (saat)", example = "5.1")
    private Double avgResolutionHours;

    @Schema(description = "Seçili dönemde girdiğim toplam worklog dakikası", example = "640")
    private Long worklogMinutesInRange;

    @Schema(description = "Seçili dönemde claim'lediğim biletlerin CSAT ortalaması (0 = yok)", example = "4.4")
    private Double csatAverage;

    @Schema(description = "Seçili dönemdeki CSAT yanıt sayım", example = "52")
    private Long csatCount;

    @Schema(description = "Claim'lediğim biletlerin durum dağılımı")
    private StatusDistributionDTO statusDistribution;

    @Schema(description = "Son N günün günlük bilet trendim (oluşan/çözülen/kapanan/SLA)")
    private TicketTimelineDTO timeline;

    @Schema(description = "Seçili dönemde günlük girdiğim worklog dakikaları")
    private List<WorklogDailyDTO> worklogTimeline;

    @Schema(description = "Seçili dönemdeki CSAT dağılımı (1–5) ve ortalama trendi")
    private AgentCsatDTO csat;

    @Schema(description = "En son claim'lediğim birkaç bilet")
    private List<RecentTicketDTO> recentTickets;
}
