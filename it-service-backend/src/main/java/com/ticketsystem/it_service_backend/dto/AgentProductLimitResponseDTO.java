package com.ticketsystem.it_service_backend.dto;

import com.ticketsystem.it_service_backend.entity.AgentProductLimit;
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
 * Agent + ürün için yapılandırılmış aktif-bilet limitinin yanıt modeli.
 * Özel limit ile ürün varsayılanından hesaplanan {@code effectiveLimit} değerini de içerir.
 */
@Schema(description = "Agent ürün limiti yanıt modeli")
public class AgentProductLimitResponseDTO {

    @Schema(description = "Agent kimliği", example = "agent-123")
    private String agentId;

    @Schema(description = "Ürün kimliği", example = "10")
    private Long productId;

    @Schema(description = "Ürün adı", example = "CRM")
    private String productName;

    @Schema(description = "Özel limit kullanılıyor mu?", example = "true")
    private boolean useCustomLimit;

    @Schema(description = "Özel maksimum aktif bilet limiti", example = "3", nullable = true)
    private Integer maxActiveTickets;

    @Schema(description = "Hesaplanmış efektif limit", example = "3", nullable = true)
    private Integer effectiveLimit;

    public static AgentProductLimitResponseDTO fromEntity(AgentProductLimit limit) {
        Product product = limit.getProduct();
        Integer effectiveLimit = Boolean.TRUE.equals(limit.getUseCustomLimit())
                ? limit.getMaxActiveTickets()
                : product.getMaxActiveTickets();

        return AgentProductLimitResponseDTO.builder()
                .agentId(limit.getAgentId())
                .productId(product.getId())
                .productName(product.getName())
                .useCustomLimit(Boolean.TRUE.equals(limit.getUseCustomLimit()))
                .maxActiveTickets(limit.getMaxActiveTickets())
                .effectiveLimit(effectiveLimit)
                .build();
    }

    public static AgentProductLimitResponseDTO fromProduct(String agentId, Product product, boolean useCustomLimit,
                                                           Integer maxActiveTickets) {
        Integer effectiveLimit = useCustomLimit ? maxActiveTickets : product.getMaxActiveTickets();

        return AgentProductLimitResponseDTO.builder()
                .agentId(agentId)
                .productId(product.getId())
                .productName(product.getName())
                .useCustomLimit(useCustomLimit)
                .maxActiveTickets(maxActiveTickets)
                .effectiveLimit(effectiveLimit)
                .build();
    }
}