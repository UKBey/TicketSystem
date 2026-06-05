package com.ticketsystem.it_service_backend.service;

import com.ticketsystem.it_service_backend.config.RateLimitConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("RateLimitConfigService")
class RateLimitConfigServiceTest {

    private RateLimitConfigService service = new RateLimitConfigService();

    @Nested
    @DisplayName("getConfig()")
    class GetConfig {

        @Test
        @DisplayName("GLOBAL_API → config from application.yml döner")
        void globalApi_returnsConfigFromYaml() {
            Optional<RateLimitConfig> result = service.getConfig("GLOBAL_API");

            assertThat(result).isPresent();
            RateLimitConfig config = result.get();
            assertThat(config.getEndpointKey()).isEqualTo("GLOBAL_API");
            assertThat(config.getMaxRequests()).isEqualTo(100);
            assertThat(config.getDurationSeconds()).isEqualTo(60);
            assertThat(config.isEnabled()).isTrue();
        }

        @Test
        @DisplayName("Olmayan anahtar → Optional.empty() döner")
        void otherEndpoint_returnsEmpty() {
            Optional<RateLimitConfig> result = service.getConfig("CLAIM_TICKET");

            assertThat(result).isEmpty();
        }
    }
}
