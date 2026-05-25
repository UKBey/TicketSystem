package com.ticketsystem.it_service_backend.dto;

import lombok.Builder;
import lombok.Data;
import java.util.List;

/**
 * Wrapper response for per-product metrics — feeds the manager dashboard's "product performance" section.
 * Contains one {@link ProductDetailDTO} row per product.
 */
@Data
@Builder
public class ProductMetricsDTO {
    private List<ProductDetailDTO> productMetrics;
}
