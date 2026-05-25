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
 * KIE Server ile REST baglantisini kuran istemciyi uygulama acilisinda olusturur.
 * Bu bean tum workflow servisleri tarafindan ortak kullanilir.
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

    /**
     * KIE Server REST client'i — workflow servisinin tek baglanti noktasi.
     *
     * <p>JSON marshalling kullanir; konfigurasyon {@code jbpm.kie-server.*}
     * property'lerinden okunur. Acilis sirasinda {@code getServerInfo()} ile
     * baglanti dogrulanir, hata olursa uygulama yine baslar — workflow ozellikleri
     * devre disi kalir ama API ayakta kalir.
     */
    @Bean
    public KieServicesClient kieServicesClient() {
        log.info("KIE Server bağlantısı yapılandırılıyor: {}", kieServerUrl);

        KieServicesConfiguration config =
                KieServicesFactory.newRestConfiguration(kieServerUrl, username, password);

        // REST payload'lari icin okunabilir ve stabil format olarak JSON secilir.
        config.setMarshallingFormat(MarshallingFormat.JSON);
        config.setTimeout(timeout);

        KieServicesClient client = KieServicesFactory.newKieServicesClient(config);

        // Uygulama acilisinda baglanti sagligini hizli bir ping ile dogrular.
        try {
            var serverInfo = client.getServerInfo().getResult();
            log.info("KIE Server bağlantısı başarılı! Server: {}, Version: {}, Capabilities: {}",
                    serverInfo.getName(),
                    serverInfo.getVersion(),
                    serverInfo.getCapabilities());
        } catch (Exception e) {
            log.error("KIE Server bağlantısı başarısız! URL: {} — Hata: {}", kieServerUrl, e.getMessage());
            log.warn("Uygulama başlatılmaya devam edecek, ancak workflow özellikleri çalışmayacak.");
        }

        return client;
    }

    /**
     * KIE cagri hatalari arttiginda gecici olarak devreyi acip zincirleme arizayi onler.
     */
    @Bean
    public CircuitBreaker kieServerCircuitBreaker() {
        CircuitBreakerConfig cbConfig = CircuitBreakerConfig.custom()
                .failureRateThreshold(50)                      // Hata orani bu esigi asarsa devre acilir.
                .slidingWindowSize(10)                          // Son N cagri uzerinden oran hesaplanir.
                .minimumNumberOfCalls(5)                        // Erken ve yanlis pozitif acilmalari azaltir.
                .waitDurationInOpenState(Duration.ofSeconds(30)) // Acik devrede bekleme suresi.
                .permittedNumberOfCallsInHalfOpenState(3)       // Yeniden denemede sinirli test cagrisina izin verir.
                .slowCallDurationThreshold(Duration.ofSeconds(10)) // Bu sureyi asan cagri yavas kabul edilir.
                .slowCallRateThreshold(50)                       // Yavas cagri orani yukselirse devre korunmaya gecer.
                .build();

        CircuitBreaker cb = CircuitBreaker.of("kieServer", cbConfig);

        cb.getEventPublisher()
                .onStateTransition(event ->
                        log.warn("KIE Server Circuit Breaker durum değişikliği: {}", event.getStateTransition()));

        return cb;
    }
}
