package com.ticketsystem.it_service_backend.config;

import org.kie.server.client.KieServicesClient;
import org.kie.server.client.KieServicesConfiguration;
import org.kie.server.client.KieServicesFactory;
import org.kie.server.api.marshalling.MarshallingFormat;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import lombok.extern.log4j.Log4j2;

import java.time.Duration;

/**
 * Standalone jBPM KIE Server ile REST üzerinden haberleşmek için
 * KieServicesClient bean'ini yapılandırır.
 *
 * Bu bean uygulama başlangıcında bir kez oluşturulur ve tüm
 * workflow servisleri tarafından paylaşılır.
 */
@Configuration
@Log4j2
public class KieClientConfig {

    @Value("${jbpm.kie-server.url}")
    private String kieServerUrl;

    @Value("${jbpm.kie-server.username}")
    private String username;

    @Value("${jbpm.kie-server.password}")
    private String password;

    @Value("${jbpm.kie-server.timeout:30000}")
    private Long timeout;

    @Bean
    public KieServicesClient kieServicesClient() {
        log.info("KIE Server bağlantısı yapılandırılıyor: {}", kieServerUrl);

        KieServicesConfiguration config =
                KieServicesFactory.newRestConfiguration(kieServerUrl, username, password);

        // JSON formatı REST haberleşmesinde en yaygın ve okunabilir format
        config.setMarshallingFormat(MarshallingFormat.JSON);
        config.setTimeout(timeout);

        KieServicesClient client = KieServicesFactory.newKieServicesClient(config);

        // Bağlantı doğrulaması
        try {
            var serverInfo = client.getServerInfo().getResult();
            log.info("✅ KIE Server bağlantısı başarılı! Server: {}, Version: {}, Capabilities: {}",
                    serverInfo.getName(),
                    serverInfo.getVersion(),
                    serverInfo.getCapabilities());
        } catch (Exception e) {
            log.error("❌ KIE Server bağlantısı başarısız! URL: {} — Hata: {}", kieServerUrl, e.getMessage());
            log.warn("⚠️ Uygulama başlatılmaya devam edecek, ancak workflow özellikleri çalışmayacak.");
        }

        return client;
    }

    /**
     * KIE Server çağrıları için Circuit Breaker bean'i.
     * Ardışık 5 hatadan sonra devre açılır (30 sn bekler).
     * Bu, KIE Server çöktüğünde Spring Boot'un thread havuzunun
     * tükenmesini önler.
     */
    @Bean
    public CircuitBreaker kieServerCircuitBreaker() {
        CircuitBreakerConfig cbConfig = CircuitBreakerConfig.custom()
                .failureRateThreshold(50)                      // %50 hata oranında aç
                .slidingWindowSize(10)                          // Son 10 çağrıyı izle
                .minimumNumberOfCalls(5)                        // Min 5 çağrıdan sonra değerlendir
                .waitDurationInOpenState(Duration.ofSeconds(30)) // Açık durumda 30 sn bekle
                .permittedNumberOfCallsInHalfOpenState(3)       // Yarı-açıkta 3 test çağrısı
                .slowCallDurationThreshold(Duration.ofSeconds(10)) // 10 sn üzeri = yavaş çağrı
                .slowCallRateThreshold(50)                       // %50 yavaş çağrıda aç
                .build();

        CircuitBreaker cb = CircuitBreaker.of("kieServer", cbConfig);

        cb.getEventPublisher()
                .onStateTransition(event ->
                        log.warn("⚡ KIE Server Circuit Breaker durum değişikliği: {}", event.getStateTransition()));

        return cb;
    }
}
