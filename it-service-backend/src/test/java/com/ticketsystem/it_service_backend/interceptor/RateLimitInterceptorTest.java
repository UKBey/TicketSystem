package com.ticketsystem.it_service_backend.interceptor;

import com.ticketsystem.it_service_backend.entity.RateLimitConfig;
import com.ticketsystem.it_service_backend.service.RateLimitConfigService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RateLimitInterceptorTest {

    @Mock RateLimitConfigService rateLimitConfigService;

    @InjectMocks RateLimitInterceptor interceptor;

    @Mock HttpServletRequest request;
    @Mock HttpServletResponse response;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private void setJwtPrincipal(String agentId) {
        Jwt jwt = mock(Jwt.class);
        when(jwt.getSubject()).thenReturn(agentId);
        Authentication auth = mock(Authentication.class);
        when(auth.getPrincipal()).thenReturn(jwt);
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    private RateLimitConfig enabledConfig(int maxRequests) {
        return RateLimitConfig.builder()
                .endpointKey("GLOBAL_API")
                .maxRequests(maxRequests)
                .durationSeconds(60)
                .enabled(true)
                .build();
    }

    @Nested
    @DisplayName("preHandle()")
    class PreHandle {

        @Test
        @DisplayName("Config yok → istek geçer")
        void noConfig_passesThrough() throws Exception {
            when(rateLimitConfigService.getConfig("GLOBAL_API")).thenReturn(Optional.empty());

            assertThat(interceptor.preHandle(request, response, new Object())).isTrue();
        }

        @Test
        @DisplayName("Config disabled → istek geçer")
        void disabledConfig_passesThrough() throws Exception {
            RateLimitConfig config = RateLimitConfig.builder()
                    .endpointKey("GLOBAL_API").maxRequests(10).durationSeconds(60).enabled(false).build();
            when(rateLimitConfigService.getConfig("GLOBAL_API")).thenReturn(Optional.of(config));

            assertThat(interceptor.preHandle(request, response, new Object())).isTrue();
        }

        @Test
        @DisplayName("JWT yok (SecurityContext boş) → istek geçer")
        void noJwt_passesThrough() throws Exception {
            when(rateLimitConfigService.getConfig("GLOBAL_API"))
                    .thenReturn(Optional.of(enabledConfig(10)));

            assertThat(interceptor.preHandle(request, response, new Object())).isTrue();
        }

        @Test
        @DisplayName("Token mevcut → istek geçer")
        void tokenAvailable_passesThrough() throws Exception {
            when(rateLimitConfigService.getConfig("GLOBAL_API"))
                    .thenReturn(Optional.of(enabledConfig(10)));
            setJwtPrincipal("agent-1");

            assertThat(interceptor.preHandle(request, response, new Object())).isTrue();
        }

        @Test
        @DisplayName("Token tükenince 429 döner ve Retry-After header set edilir")
        void tokenExhausted_returns429() throws Exception {
            when(rateLimitConfigService.getConfig("GLOBAL_API"))
                    .thenReturn(Optional.of(enabledConfig(1)));
            setJwtPrincipal("agent-exhausted");

            StringWriter sw = new StringWriter();
            when(response.getWriter()).thenReturn(new PrintWriter(sw));

            // İlk istek kota sınırı içindedir
            assertThat(interceptor.preHandle(request, response, new Object())).isTrue();

            // İkinci istek kotayı aşar
            boolean result = interceptor.preHandle(request, response, new Object());

            assertThat(result).isFalse();
            verify(response).setStatus(429);
            verify(response).setHeader(org.mockito.ArgumentMatchers.eq("Retry-After"), org.mockito.ArgumentMatchers.anyString());
        }
    }

    @Nested
    @DisplayName("invalidateBuckets()")
    class InvalidateBuckets {

        @Test
        @DisplayName("Mevcut anahtar için bucket haritası temizlenir; sonraki istek yeni bucket kullanır")
        void existingKey_removesBuckets() throws Exception {
            when(rateLimitConfigService.getConfig("GLOBAL_API"))
                    .thenReturn(Optional.of(enabledConfig(5)));
            setJwtPrincipal("agent-inv");

            interceptor.preHandle(request, response, new Object());

            interceptor.invalidateBuckets("GLOBAL_API");

            // Sonraki istek yeni bucket ile başlar (kota sıfırlanmış)
            assertThat(interceptor.preHandle(request, response, new Object())).isTrue();
        }

        @Test
        @DisplayName("Olmayan anahtar için NullPointerException fırlatılmaz")
        void nonExistentKey_doesNotThrow() {
            interceptor.invalidateBuckets("NONEXISTENT_KEY");
        }
    }
}
