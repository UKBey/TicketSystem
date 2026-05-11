package com.ticketsystem.it_service_backend.service;

import com.ticketsystem.it_service_backend.entity.RateLimitConfig;
import com.ticketsystem.it_service_backend.repository.RateLimitConfigRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RateLimitConfigServiceTest {

    @Mock RateLimitConfigRepository repository;

    @InjectMocks RateLimitConfigService service;

    @Nested
    @DisplayName("getConfig()")
    class GetConfig {

        @Test
        @DisplayName("Mevcut anahtar → Optional içinde config döner")
        void existingKey_returnsConfig() {
            RateLimitConfig config = RateLimitConfig.builder()
                    .id(1L).endpointKey("GLOBAL_API").maxRequests(10).durationSeconds(60).enabled(true).build();
            when(repository.findByEndpointKey("GLOBAL_API")).thenReturn(Optional.of(config));

            Optional<RateLimitConfig> result = service.getConfig("GLOBAL_API");

            assertThat(result).isPresent();
            assertThat(result.get().getMaxRequests()).isEqualTo(10);
        }

        @Test
        @DisplayName("Olmayan anahtar → Optional.empty() döner")
        void missingKey_returnsEmpty() {
            when(repository.findByEndpointKey("NONEXISTENT")).thenReturn(Optional.empty());

            assertThat(service.getConfig("NONEXISTENT")).isEmpty();
        }
    }

    @Nested
    @DisplayName("updateConfig()")
    class UpdateConfig {

        @Test
        @DisplayName("Mevcut ID → alanlar güncellenir ve kaydedilir")
        void existingId_updatesAndSaves() {
            RateLimitConfig existing = RateLimitConfig.builder()
                    .id(1L).endpointKey("GLOBAL_API").maxRequests(10).durationSeconds(60).enabled(true).build();
            when(repository.findById(1L)).thenReturn(Optional.of(existing));
            when(repository.save(existing)).thenReturn(existing);

            RateLimitConfig result = service.updateConfig(1L, 20, 120, false);

            assertThat(result.getMaxRequests()).isEqualTo(20);
            assertThat(result.getDurationSeconds()).isEqualTo(120);
            assertThat(result.isEnabled()).isFalse();
            verify(repository).save(existing);
        }

        @Test
        @DisplayName("Olmayan ID → EntityNotFoundException fırlatır")
        void missingId_throwsEntityNotFoundException() {
            when(repository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.updateConfig(99L, 10, 60, true))
                    .isInstanceOf(EntityNotFoundException.class)
                    .hasMessageContaining("99");
        }
    }

    @Nested
    @DisplayName("getAllConfigs()")
    class GetAllConfigs {

        @Test
        @DisplayName("Tüm kayıtlar döner")
        void returnsAllConfigs() {
            RateLimitConfig c1 = RateLimitConfig.builder().id(1L).endpointKey("GLOBAL_API").build();
            RateLimitConfig c2 = RateLimitConfig.builder().id(2L).endpointKey("CLAIM_TICKET").build();
            when(repository.findAll()).thenReturn(List.of(c1, c2));

            List<RateLimitConfig> result = service.getAllConfigs();

            assertThat(result).hasSize(2);
            assertThat(result).extracting(RateLimitConfig::getEndpointKey)
                    .containsExactlyInAnyOrder("GLOBAL_API", "CLAIM_TICKET");
        }

        @Test
        @DisplayName("DB boşsa boş liste döner")
        void emptyDb_returnsEmptyList() {
            when(repository.findAll()).thenReturn(List.of());

            assertThat(service.getAllConfigs()).isEmpty();
        }
    }
}
