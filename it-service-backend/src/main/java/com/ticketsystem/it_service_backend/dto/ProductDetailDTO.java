package com.ticketsystem.it_service_backend.dto;

import lombok.Builder;
import lombok.Data;

/**
 * Tek bir ürün için toplulaştırılmış ticket/SLA/CSAT metrikleri.
 * {@link ProductMetricsDTO} içinde liste elemanı olarak manager dashboard'unda gösterilir.
 */
@Data
@Builder
public class ProductDetailDTO {
    private Long productId;
    private String productName;
    private Long totalTickets;
    private Long openTickets;
    private Double avgResolutionHours;
    private Double csatAverage;
    private Long slaBreachCount;
    private Double slaBreachPercentage;
}
