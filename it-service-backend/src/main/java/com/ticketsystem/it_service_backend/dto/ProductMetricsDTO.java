package com.ticketsystem.it_service_backend.dto;

import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data
@Builder
public class ProductMetricsDTO {
    private List<ProductDetailDTO> productMetrics;
}
