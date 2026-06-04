package com.ticketsystem.it_service_backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Dedicated dashboard for a single product — aggregates over every ticket whose
 * {@code product_id} matches. Used by the per-product dashboard reachable from the
 * Products panel and from the manager dashboard's product table.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Tek bir ürünün dashboard'u — o ürüne ait tüm biletler üzerinden")
public class ProductDashboardDTO {

    @Schema(description = "Ürün ID'si", example = "3")
    private Long productId;

    @Schema(description = "Ürün adı", example = "Billing")
    private String productName;

    @Schema(description = "Ürüne ait toplam bilet sayısı", example = "128")
    private long totalTickets;

    @Schema(description = "Açık biletler (NEW/IN_PROGRESS/WAITING_FOR_CUSTOMER)", example = "14")
    private long openTickets;

    @Schema(description = "Çözülen + kapatılan biletler", example = "114")
    private long resolvedTickets;

    @Schema(description = "Açık biletlerden SLA'sı ihlal edilen sayısı", example = "2")
    private long slaBreachedCount;

    @Schema(description = "Açık biletlerin SLA ihlal oranı (%)", example = "14.3")
    private double slaBreachRate;

    @Schema(description = "Çözülen biletlerin ortalama çözüm süresi (saat)", example = "7.2")
    private double avgResolutionHours;

    @Schema(description = "Ürün CSAT puan ortalaması (0 = yok)", example = "4.4")
    private double csatAverage;

    @Schema(description = "Ürün CSAT yanıt sayısı", example = "57")
    private long csatCount;

    @Schema(description = "Biletlerin durum dağılımı")
    private StatusDistributionDTO statusDistribution;

    @Schema(description = "Açık biletlerin öncelik dağılımı")
    private PriorityMetricsDTO priorityDistribution;

    @Schema(description = "Son N günün günlük bilet trendi")
    private TicketTimelineDTO timeline;

    @Schema(description = "Bu ürün üzerinde çalışan ajanların performansı (ürün kapsamında)")
    private AgentPerformanceDTO topAgents;

    @Schema(description = "En son açılan birkaç bilet")
    private List<RecentTicketDTO> recentTickets;
}
