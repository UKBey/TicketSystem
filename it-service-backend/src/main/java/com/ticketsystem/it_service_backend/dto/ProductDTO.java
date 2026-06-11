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
/**
 * Lightweight DTO representing a product/support category — used by the ticket creation flow and admin pages.
 * Derived from the {@link com.ticketsystem.it_service_backend.entity.Product} entity.
 * Both name variants are returned; the client picks the one matching its UI language
 * and falls back to the other when one is missing.
 */
@Schema(description = "Ürün/destek kategorisi bilgi modeli")
public class ProductDTO {

    @Schema(description = "Ürünün benzersiz kimliği", example = "1")
    private Long id;

    @Schema(description = "Ürün/kategori adı (Türkçe). name_en ile en az biri dolu olmalı.", example = "Müşteri Yönetimi", nullable = true)
    private String nameTr;

    @Schema(description = "Ürün/kategori adı (İngilizce). name_tr ile en az biri dolu olmalı.", example = "CRM", nullable = true)
    private String nameEn;

    @Schema(description = "Ürün aktif mi?", example = "true")
    private Boolean isActive;

    @Schema(description = "Ürünün varsayılan maksimum aktif bilet limiti", example = "5", nullable = true)
    private Integer maxActiveTickets;

    public static ProductDTO fromEntity(Product product) {
        return ProductDTO.builder()
                .id(product.getId())
                .nameTr(product.getNameTr())
                .nameEn(product.getNameEn())
                .isActive(product.getIsActive())
                .maxActiveTickets(product.getMaxActiveTickets())
                .build();
    }
}
