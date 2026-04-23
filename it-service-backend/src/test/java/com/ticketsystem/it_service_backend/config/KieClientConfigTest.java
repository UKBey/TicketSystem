package com.ticketsystem.it_service_backend.config;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.junit.jupiter.api.Test;
import org.kie.server.api.marshalling.MarshallingFormat;
import org.kie.server.api.model.KieServerInfo;
import org.kie.server.api.model.ServiceResponse;
import org.kie.server.client.KieServicesClient;
import org.kie.server.client.KieServicesConfiguration;
import org.kie.server.client.KieServicesFactory;
import org.mockito.MockedStatic;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KieClientConfigTest {

    @Test
    void kieServerCircuitBreakerUsesExpectedDefaults() {
        KieClientConfig config = new KieClientConfig();

        CircuitBreaker circuitBreaker = config.kieServerCircuitBreaker();

        assertEquals("kieServer", circuitBreaker.getName());
        assertEquals(CircuitBreaker.State.CLOSED, circuitBreaker.getState());
    }

    @Test
    void kieServicesClientBuildsClientAndPingsServer() {
        KieClientConfig config = new KieClientConfig();
        ReflectionTestUtils.setField(config, "kieServerUrl", "http://localhost:8080/kie-server");
        ReflectionTestUtils.setField(config, "username", "kie-user");
        ReflectionTestUtils.setField(config, "password", "kie-pass");
        ReflectionTestUtils.setField(config, "timeout", 12_345L);

        KieServicesConfiguration kieServicesConfiguration = mock(KieServicesConfiguration.class);
        KieServicesClient kieServicesClient = mock(KieServicesClient.class);
        @SuppressWarnings("rawtypes")
        ServiceResponse serviceResponse = mock(ServiceResponse.class);
        KieServerInfo serverInfo = mock(KieServerInfo.class);

        when(serviceResponse.getResult()).thenReturn(serverInfo);
        when(kieServicesClient.getServerInfo()).thenReturn(serviceResponse);

        try (MockedStatic<KieServicesFactory> factory = org.mockito.Mockito.mockStatic(KieServicesFactory.class)) {
            factory.when(() -> KieServicesFactory.newRestConfiguration(
                    "http://localhost:8080/kie-server",
                    "kie-user",
                    "kie-pass"
            )).thenReturn(kieServicesConfiguration);
            factory.when(() -> KieServicesFactory.newKieServicesClient(kieServicesConfiguration))
                    .thenReturn(kieServicesClient);

            KieServicesClient result = config.kieServicesClient();

            assertSame(kieServicesClient, result);
            verify(kieServicesConfiguration).setMarshallingFormat(MarshallingFormat.JSON);
            verify(kieServicesConfiguration).setTimeout(12_345L);
        }
    }
}