package com.ticketsystem.it_service_backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Personal dashboard for a CUSTOMER — aggregates strictly over tickets the user
 * opened ({@code tickets.customer_id = userId}). Independent of any agent activity
 * the same user id may have, since that lives in a different relationship
 * ({@code ticket_claims.agent_id}).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Müşterinin kişisel dashboard'u — yalnızca kendi açtığı biletler üzerinden")
public class CustomerDashboardDTO {

    @Schema(description = "Açtığım toplam bilet sayısı", example = "42")
    private Long totalTickets;

    @Schema(description = "Açık biletlerim (NEW/IN_PROGRESS/WAITING_FOR_CUSTOMER)", example = "5")
    private Long openTickets;

    @Schema(description = "Çözülen + kapatılan biletlerim", example = "37")
    private Long resolvedTickets;

    @Schema(description = "Açık biletlerimden SLA'sı ihlal edilen sayısı", example = "1")
    private Long slaBreachedCount;

    @Schema(description = "Çözülen biletlerimin ortalama çözüm süresi (saat)", example = "6.4")
    private Double avgResolutionHours;

    @Schema(description = "Verdiğim CSAT puanlarının ortalaması (0 = yok)", example = "4.6")
    private Double csatAverage;

    @Schema(description = "Verdiğim CSAT yanıt sayısı", example = "30")
    private Long csatCount;

    @Schema(description = "Biletlerimin durum dağılımı")
    private StatusDistributionDTO statusDistribution;

    @Schema(description = "Son N günün günlük bilet trendim (açılan/çözülen/kapanan/SLA)")
    private TicketTimelineDTO timeline;

    @Schema(description = "En son açtığım birkaç bilet")
    private List<RecentTicketDTO> recentTickets;
}
