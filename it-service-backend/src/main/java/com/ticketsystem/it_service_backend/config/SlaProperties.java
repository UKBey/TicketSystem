package com.ticketsystem.it_service_backend.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Configuration binding that carries the {@code app.sla.*} section of
 * {@code application.yml}.
 *
 * <p>{@code SlaPolicyService} reads these values and pulls priority-based
 * SLA resolution times and warning thresholds from here. To add a new
 * priority, just append a line in {@code application.yml} — no code change
 * is needed.
 */
@Component
@ConfigurationProperties(prefix = "app.sla")
@Getter
@Setter
public class SlaProperties {

    private Map<String, PolicyConfig> policies = new HashMap<>();

    /**
     * Holds the SLA thresholds for a single priority
     * (CRITICAL/HIGH/MEDIUM/LOW). {@code resolutionHours} is the total
     * resolution time; {@code warningThresholdHours} is how many hours before
     * the deadline the warning is emitted.
     */
    @Getter
    @Setter
    public static class PolicyConfig {
        private int resolutionHours;
        private int warningThresholdHours = 2;
    }
}
