package com.ticketsystem.it_service_backend.dto;

import lombok.Builder;
import lombok.Data;
import java.util.List;

/**
 * Ürün bazlı metriklerin wrapper yanıtı; manager dashboard'unun "ürün performansı" bölümünü besler.
 * Her ürün için bir {@link ProductDetailDTO} satırı içerir.
 */
@Data
@Builder
public class ProductMetricsDTO {
    private List<ProductDetailDTO> productMetrics;
}
