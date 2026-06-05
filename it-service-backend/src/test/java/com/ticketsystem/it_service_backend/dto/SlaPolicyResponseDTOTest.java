package com.ticketsystem.it_service_backend.dto;

import com.ticketsystem.it_service_backend.entity.SlaPolicy;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SlaPolicyResponseDTOTest {

    @Test
    void fromEntity_copiesAllFields() {
        SlaPolicy entity = SlaPolicy.builder()
                .id(3L).priority("HIGH").targetResolutionHours(4).warningThresholdHours(1)
                .build();

        SlaPolicyResponseDTO dto = SlaPolicyResponseDTO.fromEntity(entity);

        assertThat(dto.getId()).isEqualTo(3L);
        assertThat(dto.getPriority()).isEqualTo("HIGH");
        assertThat(dto.getTargetResolutionHours()).isEqualTo(4);
        assertThat(dto.getWarningThresholdHours()).isEqualTo(1);
    }

    @Test
    void builder_defaultWarningThreshold_onEntityIsTwo() {
        SlaPolicy entity = SlaPolicy.builder()
                .id(1L).priority("LOW").targetResolutionHours(48).build();

        SlaPolicyResponseDTO dto = SlaPolicyResponseDTO.fromEntity(entity);

        assertThat(dto.getWarningThresholdHours()).isEqualTo(2);
    }
}
