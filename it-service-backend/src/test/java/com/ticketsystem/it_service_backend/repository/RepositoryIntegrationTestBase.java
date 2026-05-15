package com.ticketsystem.it_service_backend.repository;

import com.ticketsystem.it_service_backend.service.KieServerAdapter;
import org.kie.server.client.KieServicesClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
abstract class RepositoryIntegrationTestBase {

    static PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("ticketdb_test")
                    .withUsername("testadmin")
                    .withPassword("testpass");

    @SuppressWarnings("resource")
    static GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
                    .withExposedPorts(6379);

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

    @MockitoBean
    protected KieServicesClient kieServicesClient;

    @MockitoBean
    protected KieServerAdapter kieServerAdapter;

    @MockitoBean
    protected JwtDecoder jwtDecoder;
}