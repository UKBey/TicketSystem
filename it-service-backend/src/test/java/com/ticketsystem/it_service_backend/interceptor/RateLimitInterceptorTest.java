package com.ticketsystem.it_service_backend.interceptor;

import com.ticketsystem.it_service_backend.config.RateLimitConfig;
import com.ticketsystem.it_service_backend.service.RateLimitConfigService;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.distributed.BucketProxy;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.distributed.proxy.RemoteBucketBuilder;
import io.lettuce.core.ScanArgs;
import io.lettuce.core.ScanCursor;
import io.lettuce.core.KeyScanCursor;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RateLimitInterceptorTest {

    @Mock RateLimitConfigService rateLimitConfigService;
    @Mock ProxyManager<String> bucketProxyManager;
    @Mock StatefulRedisConnection<String, byte[]> bucketRedisConnection;

    @InjectMocks RateLimitInterceptor interceptor;

    @Mock HttpServletRequest request;
    @Mock HttpServletResponse response;

    @Mock RemoteBucketBuilder<String> bucketBuilder;
    @Mock BucketProxy bucketProxy;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void wireBucketBuilder() {
        lenient().when(bucketProxyManager.builder()).thenReturn(bucketBuilder);
        lenient().when(bucketBuilder.build(anyString(), any(Supplier.class))).thenReturn(bucketProxy);
    }

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
        @DisplayName("Config yok → istek geçer ve bucket'a uğranmaz")
        void noConfig_passesThrough() throws Exception {
            when(rateLimitConfigService.getConfig("GLOBAL_API")).thenReturn(Optional.empty());

            assertThat(interceptor.preHandle(request, response, new Object())).isTrue();
            verify(bucketProxyManager, never()).builder();
        }

        @Test
        @DisplayName("Config disabled → istek geçer ve bucket'a uğranmaz")
        void disabledConfig_passesThrough() throws Exception {
            RateLimitConfig config = RateLimitConfig.builder()
                    .endpointKey("GLOBAL_API").maxRequests(10).durationSeconds(60).enabled(false).build();
            when(rateLimitConfigService.getConfig("GLOBAL_API")).thenReturn(Optional.of(config));

            assertThat(interceptor.preHandle(request, response, new Object())).isTrue();
            verify(bucketProxyManager, never()).builder();
        }

        @Test
        @DisplayName("JWT yok → istek geçer (anonim trafik bypass)")
        void noJwt_passesThrough() throws Exception {
            when(rateLimitConfigService.getConfig("GLOBAL_API"))
                    .thenReturn(Optional.of(enabledConfig(10)));

            assertThat(interceptor.preHandle(request, response, new Object())).isTrue();
            verify(bucketProxyManager, never()).builder();
        }

        @Test
        @DisplayName("Token mevcut → istek geçer")
        void tokenAvailable_passesThrough() throws Exception {
            ConsumptionProbe probe = consumed(true, 9, 0);
            when(rateLimitConfigService.getConfig("GLOBAL_API"))
                    .thenReturn(Optional.of(enabledConfig(10)));
            setJwtPrincipal("agent-1");
            when(bucketProxy.tryConsumeAndReturnRemaining(1)).thenReturn(probe);

            assertThat(interceptor.preHandle(request, response, new Object())).isTrue();
            verify(bucketBuilder).build(eq("GLOBAL_API:agent-1"), any(Supplier.class));
        }

        @Test
        @DisplayName("Token tükenince 429 döner ve Retry-After header set edilir")
        void tokenExhausted_returns429() throws Exception {
            ConsumptionProbe probe = consumed(false, 0, 30L * 1_000_000_000L);
            when(rateLimitConfigService.getConfig("GLOBAL_API"))
                    .thenReturn(Optional.of(enabledConfig(1)));
            setJwtPrincipal("agent-exhausted");
            when(bucketProxy.tryConsumeAndReturnRemaining(1)).thenReturn(probe);

            StringWriter sw = new StringWriter();
            when(response.getWriter()).thenReturn(new PrintWriter(sw));

            boolean result = interceptor.preHandle(request, response, new Object());

            assertThat(result).isFalse();
            verify(response).setStatus(429);
            verify(response).setHeader("Retry-After", "30");
            assertThat(sw.toString()).contains("RATE_LIMIT_EXCEEDED");
        }

        private ConsumptionProbe consumed(boolean isConsumed, long remaining, long nanosToWait) {
            ConsumptionProbe probe = mock(ConsumptionProbe.class);
            lenient().when(probe.isConsumed()).thenReturn(isConsumed);
            lenient().when(probe.getRemainingTokens()).thenReturn(remaining);
            lenient().when(probe.getNanosToWaitForRefill()).thenReturn(nanosToWait);
            return probe;
        }
    }

    @Nested
    @DisplayName("invalidateBuckets()")
    class InvalidateBuckets {

        @Test
        @DisplayName("Pattern'le eşleşen tüm bucket key'leri SCAN edilip silinir")
        @SuppressWarnings("unchecked")
        void existingPattern_scansAndDeletes() {
            RedisCommands<String, byte[]> commands = mock(RedisCommands.class);
            when(bucketRedisConnection.sync()).thenReturn(commands);

            KeyScanCursor<String> firstCursor = mock(KeyScanCursor.class);
            when(firstCursor.getKeys()).thenReturn(List.of("GLOBAL_API:agent-1", "GLOBAL_API:agent-2"));
            when(firstCursor.isFinished()).thenReturn(true);
            when(commands.scan(any(ScanCursor.class), any(ScanArgs.class))).thenReturn(firstCursor);
            when(commands.del(any(String[].class))).thenReturn(2L);

            interceptor.invalidateBuckets("GLOBAL_API");

            verify(commands, times(1)).scan(any(ScanCursor.class), any(ScanArgs.class));
            verify(commands).del(any(String[].class));
        }

        @Test
        @DisplayName("Boş cursor için del çağrılmaz")
        @SuppressWarnings("unchecked")
        void noMatches_skipsDelete() {
            RedisCommands<String, byte[]> commands = mock(RedisCommands.class);
            when(bucketRedisConnection.sync()).thenReturn(commands);

            KeyScanCursor<String> cursor = mock(KeyScanCursor.class);
            when(cursor.getKeys()).thenReturn(List.of());
            when(cursor.isFinished()).thenReturn(true);
            when(commands.scan(any(ScanCursor.class), any(ScanArgs.class))).thenReturn(cursor);

            interceptor.invalidateBuckets("EMPTY_KEY");

            verify(commands, never()).del(any(String[].class));
        }

        @Test
        @DisplayName("Redis hata fırlatsa bile metot exception propagate etmez")
        void redisError_doesNotThrow() {
            when(bucketRedisConnection.sync()).thenThrow(new RuntimeException("boom"));

            org.junit.jupiter.api.Assertions.assertDoesNotThrow(
                    () -> interceptor.invalidateBuckets("GLOBAL_API"));
        }
    }
}
