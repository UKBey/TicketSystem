package com.ticketsystem.it_service_backend.dto;

import lombok.Builder;
import lombok.Data;

/**
 * Aggregated ticket/SLA/CSAT metrics for a single product.
 * Shown on the manager dashboard as an element of the list inside {@link ProductMetricsDTO}.
 */
@Data
@Builder
public class ProductDetailDTO {
    private Long productId;
    private String productNameTr;
    private String productNameEn;
    private Long totalTickets;
    private Long openTickets;
    private Double avgResolutionHours;
    private Double csatAverage;
    private Long slaBreachCount;
    private Double slaBreachPercentage;
}
