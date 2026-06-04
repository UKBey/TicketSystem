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
 * Builds the REST client used to talk to the KIE Server at application
 * startup. The bean is shared by all workflow services.
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
     * The KIE Server REST client — the workflow service's single connection
     * point.
     *
     * <p>Uses JSON marshalling and reads configuration from the
     * {@code jbpm.kie-server.*} properties. At startup the connection is
     * verified via {@code getServerInfo()}; on failure the application still
     * starts — workflow features are disabled but the rest of the API stays
     * up.
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
     * Opens the circuit briefly when KIE call failures spike, preventing
     * cascading failures.
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
                .ignoreException(KieClientConfig::isProcessInstanceNotFound) // 404 (instance yok) sagliksizlik degildir.
                .build();

        CircuitBreaker cb = CircuitBreaker.of("kieServer", cbConfig);

        cb.getEventPublisher()
                .onStateTransition(event ->
                        log.warn("KIE Server Circuit Breaker durum değişikliği: {}", event.getStateTransition()));

        return cb;
    }

    /**
     * A "process instance not found" (HTTP 404) is a deterministic, per-instance
     * outcome — the instance is permanently gone (completed &amp; pruned, or the jBPM
     * history store was reset), NOT a sign that the KIE Server is unhealthy. Counting
     * it toward the circuit breaker would wrongly trip the breaker and short-circuit
     * workflow calls for every other ticket. So it is ignored; genuine connectivity
     * failures (timeouts, refused connections, 5xx) still carry a different message
     * and continue to be recorded.
     */
    static boolean isProcessInstanceNotFound(Throwable t) {
        Throwable cause = t;
        for (int i = 0; cause != null && i < 10; cause = cause.getCause(), i++) {
            String msg = cause.getMessage();
            if (msg != null && msg.contains("Could not find process instance")) {
                return true;
            }
        }
        return false;
    }
}
