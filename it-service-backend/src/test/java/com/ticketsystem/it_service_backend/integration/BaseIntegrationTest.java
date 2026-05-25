package com.ticketsystem.it_service_backend.integration;

import com.ticketsystem.it_service_backend.service.KieServerAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.kie.server.client.KieServicesClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Abstract base class inherited by every integration test.
 *
 * <p>Responsibilities:
 * <ul>
 *     <li>Spins up a shared PostgreSQL Testcontainer and manages its
 *         lifecycle.</li>
 *     <li>Routes Spring datasource settings to the container via
 *         {@code @DynamicPropertySource}.</li>
 *     <li>Stubs external services (jBPM KIE Server) with
 *         {@code @MockitoBean} to remove any single point of failure.</li>
 *     <li>Runs Flyway migrations on the container automatically.</li>
 *     <li>Provides ready-to-use {@code MockMvc} infrastructure for HTTP
 *         tests.</li>
 * </ul>
 *
 * <p><strong>Security:</strong> Keycloak/JWT validation is disabled in the
 * test profile. Each test method authenticates with a mock JWT token via
 * {@code SecurityMockMvcRequestPostProcessors.jwt()}.
 *
 * <p><strong>Singleton container pattern:</strong> The container is
 * declared {@code static} and shared across every IT class, removing the
 * cost of spinning up a new container per test class.
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
     * Shared container for the rate-limit interceptor's Redis connection.
     * Reused across every IT class (singleton pattern).
     */
    @SuppressWarnings("resource")
    static GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
                    .withExposedPorts(6379);

    /**
     * Before the Spring Boot context is built, injects the live Testcontainer
     * JDBC URL, username, password and the Redis host/port into the Spring
     * properties.
     */
    @DynamicPropertySource
    static void configureTestContainers(DynamicPropertyRegistry registry) {
        if (!POSTGRES.isRunning()) {
            POSTGRES.start();
        }
        if (!REDIS.isRunning()) {
            REDIS.start();
        }

        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);

        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    // =========================================================================
    // Mock Beans — Dis Bagimliliklarin Izolasyonu
    // =========================================================================

    /**
     * KieServicesClient: at startup KieClientConfig calls
     * {@code KieServicesFactory.newKieServicesClient()} and pings the
     * server. This mock prevents the real KIE Server connection.
     */
    @MockitoBean
    protected KieServicesClient kieServicesClient;

    /**
     * KieServerAdapter: fully stubs the WorkflowService → KieServerAdapter
     * → KieServicesClient chain. This way the
     * {@code WorkflowEventListener.onTicketCreated()} call triggered during
     * ticket creation does not fail.
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

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    /**
     * Truncates the business tables before every test. Because the
     * Postgres container is shared as a singleton, data left behind by one
     * IT class must not break the next class's "empty DB" assumption.
     * Reference tables managed by Flyway (e.g. {@code sla_policies}) are
     * preserved.
     */
    @BeforeEach
    void truncateBusinessTables() {
        jdbcTemplate.execute("""
                TRUNCATE TABLE
                    ticket_comments,
                    ticket_topics,
                    ticket_audit_logs,
                    ticket_worklogs,
                    ticket_attachments,
                    csat_surveys,
                    notifications,
                    tickets,
                    user_products,
                    users,
                    products
                CASCADE
                """);
    }
}
