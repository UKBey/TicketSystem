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
@Schema(description = "Ürün için maksimum aktif bilet limiti güncelleme modeli")
public class ProductLimitUpdateRequestDTO {

    @Schema(description = "Ürünün varsayılan maksimum aktif bilet limiti", example = "5", nullable = true)
    private Integer maxActiveTickets;
}