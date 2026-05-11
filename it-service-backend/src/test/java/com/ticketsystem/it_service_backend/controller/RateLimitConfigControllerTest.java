package com.ticketsystem.it_service_backend.controller;

import com.ticketsystem.it_service_backend.dto.RateLimitConfigResponseDTO;
import com.ticketsystem.it_service_backend.dto.RateLimitConfigUpdateDTO;
import com.ticketsystem.it_service_backend.entity.RateLimitConfig;
import com.ticketsystem.it_service_backend.interceptor.RateLimitInterceptor;
import com.ticketsystem.it_service_backend.service.RateLimitConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RateLimitConfigControllerTest {

    @Mock RateLimitConfigService rateLimitConfigService;
    @Mock RateLimitInterceptor rateLimitInterceptor;

    RateLimitConfigController controller;

    @BeforeEach
    void setUp() {
        controller = new RateLimitConfigController(rateLimitConfigService, rateLimitInterceptor);
    }

    @Test
    @DisplayName("getAllConfigs → tüm konfigürasyonlar DTO'ya map edilerek döner")
    void getAllConfigs_returnsMappedList() {
        RateLimitConfig config = RateLimitConfig.builder()
                .id(1L).endpointKey("GLOBAL_API").maxRequests(10)
                .durationSeconds(60).enabled(true).build();
        when(rateLimitConfigService.getAllConfigs()).thenReturn(List.of(config));

        ResponseEntity<List<RateLimitConfigResponseDTO>> response = controller.getAllConfigs();

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).hasSize(1);
        assertThat(response.getBody().get(0).getEndpointKey()).isEqualTo("GLOBAL_API");
        assertThat(response.getBody().get(0).getMaxRequests()).isEqualTo(10);
        assertThat(response.getBody().get(0).getDurationSeconds()).isEqualTo(60);
        assertThat(response.getBody().get(0).isEnabled()).isTrue();
    }

    @Test
    @DisplayName("getAllConfigs → boş DB için boş liste döner")
    void getAllConfigs_emptyDb_returnsEmptyBody() {
        when(rateLimitConfigService.getAllConfigs()).thenReturn(List.of());

        ResponseEntity<List<RateLimitConfigResponseDTO>> response = controller.getAllConfigs();

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isEmpty();
    }

    @Test
    @DisplayName("updateConfig → servisi çağırır, bucket'ları invalidate eder ve DTO döner")
    void updateConfig_persistsAndInvalidatesBuckets() {
        RateLimitConfigUpdateDTO dto = RateLimitConfigUpdateDTO.builder()
                .maxRequests(20).durationSeconds(120).enabled(false).build();

        RateLimitConfig updated = RateLimitConfig.builder()
                .id(1L).endpointKey("GLOBAL_API").maxRequests(20)
                .durationSeconds(120).enabled(false).build();
        when(rateLimitConfigService.updateConfig(1L, 20, 120, false)).thenReturn(updated);

        ResponseEntity<RateLimitConfigResponseDTO> response = controller.updateConfig(1L, dto);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMaxRequests()).isEqualTo(20);
        assertThat(response.getBody().getDurationSeconds()).isEqualTo(120);
        assertThat(response.getBody().isEnabled()).isFalse();

        verify(rateLimitConfigService).updateConfig(1L, 20, 120, false);
        verify(rateLimitInterceptor).invalidateBuckets("GLOBAL_API");
    }

    @Test
    @DisplayName("updateConfig → description ve updatedAt alanları DTO'ya kopyalanır")
    void updateConfig_copiesAllFieldsToDto() {
        RateLimitConfigUpdateDTO dto = RateLimitConfigUpdateDTO.builder()
                .maxRequests(5).durationSeconds(30).enabled(true).build();

        RateLimitConfig updated = RateLimitConfig.builder()
                .id(2L).endpointKey("CLAIM_TICKET")
                .description("Bilet claim limiti")
                .maxRequests(5).durationSeconds(30).enabled(true).build();
        when(rateLimitConfigService.updateConfig(2L, 5, 30, true)).thenReturn(updated);

        ResponseEntity<RateLimitConfigResponseDTO> response = controller.updateConfig(2L, dto);

        assertThat(response.getBody().getDescription()).isEqualTo("Bilet claim limiti");
        assertThat(response.getBody().getEndpointKey()).isEqualTo("CLAIM_TICKET");
    }
}
