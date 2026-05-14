package com.ticketsystem.it_service_backend.service;

import com.ticketsystem.it_service_backend.config.SlaProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SlaPolicyServiceTest {

    private SlaPolicyService service;

    @BeforeEach
    void setUp() {
        SlaProperties props = new SlaProperties();
        props.setPolicies(Map.of(
                "LOW",      policy(72, 2),
                "MEDIUM",   policy(24, 2),
                "HIGH",     policy(4,  1),
                "CRITICAL", policy(1,  0)
        ));
        service = new SlaPolicyService(props);
    }

    private SlaProperties.PolicyConfig policy(int resolutionHours, int warningHours) {
        SlaProperties.PolicyConfig cfg = new SlaProperties.PolicyConfig();
        cfg.setResolutionHours(resolutionHours);
        cfg.setWarningThresholdHours(warningHours);
        return cfg;
    }

    @Nested
    @DisplayName("getSlaDurationMs()")
    class GetSlaDurationMs {

        @Test
        @DisplayName("null öncelik → MEDIUM varsayılanı (12 saat)")
        void nullPriority_returnsMediumDefault() {
            assertThat(service.getSlaDurationMs(null)).isEqualTo(12L * 3_600_000L);
        }

        @Test
        @DisplayName("Config'den LOW → 72 saat")
        void low_returns72h() {
            assertThat(service.getSlaDurationMs("LOW")).isEqualTo(72L * 3_600_000L);
        }

        @Test
        @DisplayName("Config'den MEDIUM → 24 saat")
        void medium_returns24h() {
            assertThat(service.getSlaDurationMs("MEDIUM")).isEqualTo(24L * 3_600_000L);
        }

        @Test
        @DisplayName("Config'den HIGH → 4 saat")
        void high_returns4h() {
            assertThat(service.getSlaDurationMs("HIGH")).isEqualTo(4L * 3_600_000L);
        }

        @Test
        @DisplayName("Config'den CRITICAL → 1 saat")
        void critical_returns1h() {
            assertThat(service.getSlaDurationMs("CRITICAL")).isEqualTo(1L * 3_600_000L);
        }

        @Test
        @DisplayName("Küçük harf priority → büyük harfe çevrilir")
        void lowercasePriority_normalised() {
            assertThat(service.getSlaDurationMs("high")).isEqualTo(4L * 3_600_000L);
        }

        @Test
        @DisplayName("Config'de olmayan priority → 12 saat varsayılan")
        void unknownPriority_returnsDefault() {
            assertThat(service.getSlaDurationMs("UNKNOWN")).isEqualTo(12L * 3_600_000L);
        }

        @Test
        @DisplayName("resolutionHours = 0 olan config → varsayılana düşer")
        void zeroPolicyHours_fallsBackToDefault() {
            SlaProperties props = new SlaProperties();
            props.setPolicies(Map.of("MEDIUM", policy(0, 2)));
            SlaPolicyService svc = new SlaPolicyService(props);

            assertThat(svc.getSlaDurationMs("MEDIUM")).isEqualTo(12L * 3_600_000L);
        }
    }

    @Nested
    @DisplayName("getWarningThresholdHours()")
    class GetWarningThresholdHours {

        @Test
        @DisplayName("null öncelik → 2 saat varsayılan")
        void nullPriority_returns2() {
            assertThat(service.getWarningThresholdHours(null)).isEqualTo(2);
        }

        @Test
        @DisplayName("Config'de olmayan priority → 2 varsayılan")
        void unknownPriority_returns2() {
            assertThat(service.getWarningThresholdHours("UNKNOWN")).isEqualTo(2);
        }

        @Test
        @DisplayName("Config'den CRITICAL → 0 (uyarı kapalı)")
        void critical_returns0() {
            assertThat(service.getWarningThresholdHours("CRITICAL")).isEqualTo(0);
        }

        @Test
        @DisplayName("Config'den HIGH → 1 saat")
        void high_returns1() {
            assertThat(service.getWarningThresholdHours("HIGH")).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("defaultMs branch coverage")
    class DefaultMsBranches {

        private SlaPolicyService emptyConfigService;

        @BeforeEach
        void setUp() {
            // Empty policy map forces defaultMs() path for every priority lookup.
            SlaProperties props = new SlaProperties();
            props.setPolicies(Map.of());
            emptyConfigService = new SlaPolicyService(props);
        }

        @Test
        void critical_defaultsTo1Hour() {
            assertThat(emptyConfigService.getSlaDurationMs("CRITICAL")).isEqualTo(3_600_000L);
        }

        @Test
        void high_defaultsTo4Hours() {
            assertThat(emptyConfigService.getSlaDurationMs("HIGH")).isEqualTo(4L * 3_600_000L);
        }

        @Test
        void low_defaultsTo24Hours() {
            assertThat(emptyConfigService.getSlaDurationMs("LOW")).isEqualTo(24L * 3_600_000L);
        }

        @Test
        void medium_defaultsTo12Hours() {
            assertThat(emptyConfigService.getSlaDurationMs("MEDIUM")).isEqualTo(12L * 3_600_000L);
        }
    }
}
