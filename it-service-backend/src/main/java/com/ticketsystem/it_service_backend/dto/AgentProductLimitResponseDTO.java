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
 * Response model for the active-ticket limit configured on an agent + product pair.
 * Also exposes the {@code effectiveLimit} computed from the custom limit and the product default.
 */
@Schema(description = "Agent ürün limiti yanıt modeli")
public class AgentProductLimitResponseDTO {

    @Schema(description = "Agent kimliği", example = "agent-123")
    private String agentId;

    @Schema(description = "Ürün kimliği", example = "10")
    private Long productId;

    @Schema(description = "Ürün adı (Türkçe varyant)", example = "Müşteri Yönetimi", nullable = true)
    private String productNameTr;

    @Schema(description = "Ürün adı (İngilizce varyant)", example = "CRM", nullable = true)
    private String productNameEn;

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
                .productNameTr(product.getNameTr())
                .productNameEn(product.getNameEn())
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
                .productNameTr(product.getNameTr())
                .productNameEn(product.getNameEn())
                .useCustomLimit(useCustomLimit)
                .maxActiveTickets(maxActiveTickets)
                .effectiveLimit(effectiveLimit)
                .build();
    }
}