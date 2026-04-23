package com.ticketsystem.it_service_backend.dto;

import com.ticketsystem.it_service_backend.entity.Product;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Ürün/destek kategorisi bilgi modeli")
public class ProductDTO {

    @Schema(description = "Ürünün benzersiz kimliği", example = "1")
    private Long id;

    @Schema(description = "Ürün/kategori adı", example = "CRM")
    private String name;

    @Schema(description = "Ürün aktif mi?", example = "true")
    private Boolean isActive;

    public static ProductDTO fromEntity(Product product) {
        return ProductDTO.builder()
                .id(product.getId())
                .name(product.getName())
                .isActive(product.getIsActive())
                .build();
    }
}
