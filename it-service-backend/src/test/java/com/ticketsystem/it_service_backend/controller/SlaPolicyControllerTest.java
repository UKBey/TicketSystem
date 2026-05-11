package com.ticketsystem.it_service_backend.controller;

import com.ticketsystem.it_service_backend.dto.SlaPolicyResponseDTO;
import com.ticketsystem.it_service_backend.dto.SlaPolicyUpdateDTO;
import com.ticketsystem.it_service_backend.entity.SlaPolicy;
import com.ticketsystem.it_service_backend.service.SlaPolicyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.NoSuchElementException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SlaPolicyControllerTest {

    @Mock SlaPolicyService slaPolicyService;

    SlaPolicyController controller;

    @BeforeEach
    void setUp() {
        controller = new SlaPolicyController(slaPolicyService);
    }

    @Test
    @DisplayName("getAllPolicies → servisten gelen liste DTO'ya map edilerek döner")
    void getAllPolicies_returnsMappedList() {
        SlaPolicy critical = SlaPolicy.builder().id(1L).priority("CRITICAL")
                .targetResolutionHours(1).warningThresholdHours(1).build();
        SlaPolicy high = SlaPolicy.builder().id(2L).priority("HIGH")
                .targetResolutionHours(4).warningThresholdHours(2).build();
        when(slaPolicyService.getAllPolicies()).thenReturn(List.of(critical, high));

        ResponseEntity<List<SlaPolicyResponseDTO>> response = controller.getAllPolicies();

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).hasSize(2);
        assertThat(response.getBody().get(0).getPriority()).isEqualTo("CRITICAL");
        assertThat(response.getBody().get(0).getTargetResolutionHours()).isEqualTo(1);
        assertThat(response.getBody().get(1).getPriority()).isEqualTo("HIGH");
        assertThat(response.getBody().get(1).getWarningThresholdHours()).isEqualTo(2);
    }

    @Test
    @DisplayName("getAllPolicies → boş liste dönebilir")
    void getAllPolicies_emptyList_returnsEmptyBody() {
        when(slaPolicyService.getAllPolicies()).thenReturn(List.of());

        ResponseEntity<List<SlaPolicyResponseDTO>> response = controller.getAllPolicies();

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isEmpty();
    }

    @Test
    @DisplayName("updatePolicy → servis günceller ve güncel DTO döner")
    void updatePolicy_callsServiceAndReturnsMappedDto() {
        SlaPolicyUpdateDTO dto = new SlaPolicyUpdateDTO();
        dto.setTargetResolutionHours(6);
        dto.setWarningThresholdHours(3);

        SlaPolicy updated = SlaPolicy.builder().id(1L).priority("HIGH")
                .targetResolutionHours(6).warningThresholdHours(3).build();
        when(slaPolicyService.updatePolicy(1L, 6, 3)).thenReturn(updated);

        ResponseEntity<SlaPolicyResponseDTO> response = controller.updatePolicy(1L, dto);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getId()).isEqualTo(1L);
        assertThat(response.getBody().getTargetResolutionHours()).isEqualTo(6);
        assertThat(response.getBody().getWarningThresholdHours()).isEqualTo(3);
        verify(slaPolicyService).updatePolicy(1L, 6, 3);
    }

    @Test
    @DisplayName("updatePolicy → servis NoSuchElementException fırlatırsa controller de fırlatır")
    void updatePolicy_whenNotFound_propagatesException() {
        SlaPolicyUpdateDTO dto = new SlaPolicyUpdateDTO();
        dto.setTargetResolutionHours(4);
        dto.setWarningThresholdHours(2);

        when(slaPolicyService.updatePolicy(99L, 4, 2))
                .thenThrow(new NoSuchElementException("SLA politikası bulunamadı: id=99"));

        assertThatThrownBy(() -> controller.updatePolicy(99L, dto))
                .isInstanceOf(NoSuchElementException.class);
    }
}
