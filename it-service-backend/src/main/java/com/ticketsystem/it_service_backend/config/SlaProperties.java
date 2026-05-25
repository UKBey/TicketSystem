package com.ticketsystem.it_service_backend.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * {@code application.yml} icindeki {@code app.sla.*} bolumunu tasiyan
 * konfigurasyon binding'i.
 *
 * <p>{@code SlaPolicyService} bu degerleri okur ve oncelik bazli SLA cozum
 * suresi / uyari esigini buradan alir. Yeni bir oncelik eklemek icin sadece
 * {@code application.yml}'a satir eklemek yeterli — kod degisikligi gerekmez.
 */
@Component
@ConfigurationProperties(prefix = "app.sla")
@Getter
@Setter
public class SlaProperties {

    private Map<String, PolicyConfig> policies = new HashMap<>();

    /**
     * Tek bir oncelik (CRITICAL/HIGH/MEDIUM/LOW) icin SLA esiklerini tutar.
     * {@code resolutionHours} cozumun toplam suresi, {@code warningThresholdHours}
     * ise deadline'a kac saat kala uyari yayilacagini belirler.
     */
    @Getter
    @Setter
    public static class PolicyConfig {
        private int resolutionHours;
        private int warningThresholdHours = 2;
    }
}
