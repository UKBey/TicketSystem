package com.ticketsystem.it_service_backend.service;

import com.ticketsystem.it_service_backend.entity.SlaPolicy;
import com.ticketsystem.it_service_backend.repository.SlaPolicyJpaRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SlaPolicyServiceTest {

    @Mock SlaPolicyJpaRepository slaPolicyJpaRepository;

    @InjectMocks SlaPolicyService slaPolicyService;

    @Nested
    @DisplayName("getSlaDurationMs()")
    class GetSlaDurationMs {

        @Test
        @DisplayName("null öncelik → MEDIUM varsayılanı döner (12 saat)")
        void nullPriority_returnsMediumDefault() {
            assertThat(slaPolicyService.getSlaDurationMs(null)).isEqualTo(12L * 3_600_000L);
        }

        @Test
        @DisplayName("DB kaydı varsa targetResolutionHours × 3_600_000 döner")
        void dbHit_returnsHoursInMs() {
            SlaPolicy policy = SlaPolicy.builder().priority("HIGH").targetResolutionHours(4).build();
            when(slaPolicyJpaRepository.findByPriority("HIGH")).thenReturn(Optional.of(policy));

            assertThat(slaPolicyService.getSlaDurationMs("HIGH")).isEqualTo(4L * 3_600_000L);
        }

        @Test
        @DisplayName("DB kaydı yok, CRITICAL → 1 saat varsayılan")
        void dbMiss_critical_returns1h() {
            when(slaPolicyJpaRepository.findByPriority("CRITICAL")).thenReturn(Optional.empty());

            assertThat(slaPolicyService.getSlaDurationMs("CRITICAL")).isEqualTo(1L * 3_600_000L);
        }

        @Test
        @DisplayName("DB kaydı yok, HIGH → 4 saat varsayılan")
        void dbMiss_high_returns4h() {
            when(slaPolicyJpaRepository.findByPriority("HIGH")).thenReturn(Optional.empty());

            assertThat(slaPolicyService.getSlaDurationMs("HIGH")).isEqualTo(4L * 3_600_000L);
        }

        @Test
        @DisplayName("DB kaydı yok, MEDIUM → 12 saat varsayılan")
        void dbMiss_medium_returns12h() {
            when(slaPolicyJpaRepository.findByPriority("MEDIUM")).thenReturn(Optional.empty());

            assertThat(slaPolicyService.getSlaDurationMs("MEDIUM")).isEqualTo(12L * 3_600_000L);
        }

        @Test
        @DisplayName("DB kaydı yok, LOW → 24 saat varsayılan")
        void dbMiss_low_returns24h() {
            when(slaPolicyJpaRepository.findByPriority("LOW")).thenReturn(Optional.empty());

            assertThat(slaPolicyService.getSlaDurationMs("LOW")).isEqualTo(24L * 3_600_000L);
        }

        @Test
        @DisplayName("Bilinmeyen öncelik → 12 saat varsayılan")
        void dbMiss_unknown_returns12h() {
            when(slaPolicyJpaRepository.findByPriority("UNKNOWN")).thenReturn(Optional.empty());

            assertThat(slaPolicyService.getSlaDurationMs("unknown")).isEqualTo(12L * 3_600_000L);
        }
    }

    @Nested
    @DisplayName("getWarningThresholdHours()")
    class GetWarningThresholdHours {

        @Test
        @DisplayName("null öncelik → 2 saat varsayılan")
        void nullPriority_returns2() {
            assertThat(slaPolicyService.getWarningThresholdHours(null)).isEqualTo(2);
        }

        @Test
        @DisplayName("DB kaydı yok → 2 saat varsayılan")
        void dbMiss_returns2() {
            when(slaPolicyJpaRepository.findByPriority("HIGH")).thenReturn(Optional.empty());

            assertThat(slaPolicyService.getWarningThresholdHours("HIGH")).isEqualTo(2);
        }

        @Test
        @DisplayName("DB kaydı var → policy değeri döner")
        void dbHit_returnsPolicyValue() {
            SlaPolicy policy = SlaPolicy.builder()
                    .priority("CRITICAL")
                    .targetResolutionHours(1)
                    .warningThresholdHours(1)
                    .build();
            when(slaPolicyJpaRepository.findByPriority("CRITICAL")).thenReturn(Optional.of(policy));

            assertThat(slaPolicyService.getWarningThresholdHours("CRITICAL")).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("getAllPolicies()")
    class GetAllPolicies {

        @Test
        @DisplayName("Politikalar öncelik sırasıyla döner: CRITICAL, HIGH, MEDIUM, LOW")
        void returnsSortedByPriorityOrder() {
            List<SlaPolicy> unordered = List.of(
                    SlaPolicy.builder().priority("LOW").targetResolutionHours(24).build(),
                    SlaPolicy.builder().priority("CRITICAL").targetResolutionHours(1).build(),
                    SlaPolicy.builder().priority("MEDIUM").targetResolutionHours(12).build(),
                    SlaPolicy.builder().priority("HIGH").targetResolutionHours(4).build()
            );
            when(slaPolicyJpaRepository.findAll()).thenReturn(unordered);

            List<SlaPolicy> result = slaPolicyService.getAllPolicies();

            assertThat(result)
                    .extracting(SlaPolicy::getPriority)
                    .containsExactly("CRITICAL", "HIGH", "MEDIUM", "LOW");
        }

        @Test
        @DisplayName("Boş liste → boş liste döner")
        void emptyList_returnsEmpty() {
            when(slaPolicyJpaRepository.findAll()).thenReturn(List.of());

            assertThat(slaPolicyService.getAllPolicies()).isEmpty();
        }
    }

    @Nested
    @DisplayName("updatePolicy()")
    class UpdatePolicy {

        @Test
        @DisplayName("Mevcut ID → güncellenen politikayı döner")
        void existingId_updatesAndReturns() {
            SlaPolicy existing = SlaPolicy.builder()
                    .id(1L).priority("HIGH")
                    .targetResolutionHours(4).warningThresholdHours(2)
                    .build();
            when(slaPolicyJpaRepository.findById(1L)).thenReturn(Optional.of(existing));
            when(slaPolicyJpaRepository.save(existing)).thenReturn(existing);

            SlaPolicy result = slaPolicyService.updatePolicy(1L, 6, 3);

            assertThat(result.getTargetResolutionHours()).isEqualTo(6);
            assertThat(result.getWarningThresholdHours()).isEqualTo(3);
            verify(slaPolicyJpaRepository).save(existing);
        }

        @Test
        @DisplayName("Olmayan ID → NoSuchElementException fırlatır")
        void missingId_throwsNoSuchElementException() {
            when(slaPolicyJpaRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> slaPolicyService.updatePolicy(99L, 4, 2))
                    .isInstanceOf(NoSuchElementException.class)
                    .hasMessageContaining("99");
        }
    }
}
