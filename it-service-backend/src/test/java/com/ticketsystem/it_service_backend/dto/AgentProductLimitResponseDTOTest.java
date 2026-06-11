package com.ticketsystem.it_service_backend.dto;

import com.ticketsystem.it_service_backend.entity.AgentProductLimit;
import com.ticketsystem.it_service_backend.entity.Product;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AgentProductLimitResponseDTOTest {

    private Product product(int max) {
        return Product.builder().id(10L).nameEn("CRM").maxActiveTickets(max).build();
    }

    @Test
    void fromEntity_customLimitTrue_returnsCustomMax() {
        AgentProductLimit limit = AgentProductLimit.builder()
                .agentId("a-1").product(product(5)).useCustomLimit(true).maxActiveTickets(2).build();

        AgentProductLimitResponseDTO dto = AgentProductLimitResponseDTO.fromEntity(limit);

        assertThat(dto.isUseCustomLimit()).isTrue();
        assertThat(dto.getEffectiveLimit()).isEqualTo(2);
        assertThat(dto.getProductId()).isEqualTo(10L);
        assertThat(dto.getProductNameEn()).isEqualTo("CRM");
    }

    @Test
    void fromEntity_customLimitFalse_returnsProductMax() {
        AgentProductLimit limit = AgentProductLimit.builder()
                .agentId("a-1").product(product(5)).useCustomLimit(false).maxActiveTickets(2).build();

        AgentProductLimitResponseDTO dto = AgentProductLimitResponseDTO.fromEntity(limit);

        assertThat(dto.isUseCustomLimit()).isFalse();
        assertThat(dto.getEffectiveLimit()).isEqualTo(5);
    }

    @Test
    void fromEntity_customLimitNull_defaultsToFalseUsesProductLimit() {
        AgentProductLimit limit = AgentProductLimit.builder()
                .agentId("a-1").product(product(5)).useCustomLimit(null).maxActiveTickets(2).build();

        AgentProductLimitResponseDTO dto = AgentProductLimitResponseDTO.fromEntity(limit);

        assertThat(dto.isUseCustomLimit()).isFalse();
        assertThat(dto.getEffectiveLimit()).isEqualTo(5);
    }

    @Test
    void fromProduct_useCustomLimitTrue_returnsCustomMax() {
        AgentProductLimitResponseDTO dto = AgentProductLimitResponseDTO.fromProduct(
                "a-1", product(8), true, 3);

        assertThat(dto.getAgentId()).isEqualTo("a-1");
        assertThat(dto.getEffectiveLimit()).isEqualTo(3);
        assertThat(dto.isUseCustomLimit()).isTrue();
    }

    @Test
    void fromProduct_useCustomLimitFalse_returnsProductMax() {
        AgentProductLimitResponseDTO dto = AgentProductLimitResponseDTO.fromProduct(
                "a-1", product(8), false, 3);

        assertThat(dto.getEffectiveLimit()).isEqualTo(8);
        assertThat(dto.isUseCustomLimit()).isFalse();
    }
}
