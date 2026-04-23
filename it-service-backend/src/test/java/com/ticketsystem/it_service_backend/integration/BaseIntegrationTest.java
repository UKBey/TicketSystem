package com.ticketsystem.it_service_backend.integration;

import com.ticketsystem.it_service_backend.service.KieServerAdapter;
import org.kie.server.client.KieServicesClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Tum entegrasyon testlerinin miras aldigi soyut temel sinif.
 *
 * <p>Sorumluluklar:
 * <ul>
 *     <li>Paylasilmis bir PostgreSQL Testcontainer olusturur ve yasam dongusunu yonetir.</li>
 *     <li>{@code @DynamicPropertySource} ile Spring datasource ayarlarini container'a yonlendirir.</li>
 *     <li>Dis servisleri (jBPM KIE Server) {@code @MockitoBean} ile stub eder; SPOF'u onler.</li>
 *     <li>Flyway migrasyonlari otomatik olarak container uzerinde calisir.</li>
 *     <li>{@code MockMvc} ile HTTP testlerine hazir altyapi sunar.</li>
 * </ul>
 *
 * <p><strong>Guvenlik:</strong> Keycloak/JWT dogrulamasi test profilinde devre disi
 * birakilir. Her test metodu {@code SecurityMockMvcRequestPostProcessors.jwt()} ile
 * mock JWT token kullanarak kimlik dogrulamasi yapar.
 *
 * <p><strong>Singleton Container Deseni:</strong> Container {@code static} olarak tanimlanir
 * ve tum IT siniflari arasinda paylasilir. Bu, her test sinifi icin yeni container
 * baslatma maliyetini ortadan kaldirir.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class BaseIntegrationTest {

    // =========================================================================
    // Testcontainers — Paylasilmis PostgreSQL Container (Singleton Deseni)
    // =========================================================================

        static PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("ticketdb_test")
                    .withUsername("testadmin")
                    .withPassword("testpass");

    /**
     * Spring Boot baglam olusturulmadan once, Testcontainer'in gercek
     * JDBC URL, kullanici adi ve sifresini Spring property'lerine enjekte eder.
     * Boylece Flyway ve JPA dogru veritabanina baglanir.
     */
    @DynamicPropertySource
    static void configureTestDatabase(DynamicPropertyRegistry registry) {
        if (!POSTGRES.isRunning()) {
            POSTGRES.start();
        }

        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    // =========================================================================
    // Mock Beans — Dis Bagimliliklarin Izolasyonu
    // =========================================================================

    /**
     * KieServicesClient: KieClientConfig sinifi uygulama baslatilirken
     * {@code KieServicesFactory.newKieServicesClient()} cagirir ve sunucuya
     * ping atar. Bu mock, gercek KIE Server baglantisinini onler.
     */
    @MockitoBean
    protected KieServicesClient kieServicesClient;

    /**
     * KieServerAdapter: WorkflowService -> KieServerAdapter -> KieServicesClient
     * zincirini tamamen stub eder. Boylece bilet olusturma sirasinda tetiklenen
     * {@code WorkflowEventListener.onTicketCreated()} cagrisinda hata olmaz.
     */
    @MockitoBean
    protected KieServerAdapter kieServerAdapter;

    @MockitoBean
    protected JwtDecoder jwtDecoder;

    // =========================================================================
    // MockMvc — HTTP Test Altyapisi
    // =========================================================================

    @Autowired
    protected MockMvc mockMvc;
}
