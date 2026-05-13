package com.ticketsystem.it_service_backend.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
@ConfigurationProperties(prefix = "app.sla")
@Getter
@Setter
public class SlaProperties {

    private Map<String, PolicyConfig> policies = new HashMap<>();

    @Getter
    @Setter
    public static class PolicyConfig {
        private int resolutionHours;
        private int warningThresholdHours = 2;
    }
}
