package com.ticketsystem.llmservice.interceptor;

import com.ticketsystem.llmservice.config.RateLimitProperties;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.distributed.BucketProxy;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.distributed.proxy.RemoteBucketBuilder;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RateLimitInterceptorTest {

    @Mock ProxyManager<String> bucketProxyManager;
    @Mock RemoteBucketBuilder<String> bucketBuilder;
    @Mock BucketProxy bucketProxy;
    @Mock HttpServletRequest request;
    @Mock HttpServletResponse response;

    private RateLimitProperties properties;
    private RateLimitInterceptor interceptor;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        properties = new RateLimitProperties();
        interceptor = new RateLimitInterceptor(properties, bucketProxyManager);
        lenient().when(bucketProxyManager.builder()).thenReturn(bucketBuilder);
        lenient().when(bucketBuilder.build(anyString(), any(Supplier.class))).thenReturn(bucketProxy);
    }

    private ConsumptionProbe consumed(boolean isConsumed, long remaining, long nanosToWait) {
        ConsumptionProbe probe = mock(ConsumptionProbe.class);
        lenient().when(probe.isConsumed()).thenReturn(isConsumed);
        lenient().when(probe.getRemainingTokens()).thenReturn(remaining);
        lenient().when(probe.getNanosToWaitForRefill()).thenReturn(nanosToWait);
        return probe;
    }

    @Nested
    @DisplayName("preHandle()")
    class PreHandle {

        @Test
        @DisplayName("Disabled → istek geçer ve bucket'a uğranmaz")
        void disabled_passesThrough() throws Exception {
            properties.setEnabled(false);

            assertThat(interceptor.preHandle(request, response, new Object())).isTrue();
            verify(bucketProxyManager, never()).builder();
        }

        @Test
        @DisplayName("Token mevcut → istek geçer ve bucket key remote-addr ile şekillenir")
        void tokenAvailable_passesThrough() throws Exception {
            ConsumptionProbe probe = consumed(true, 0, 0);
            when(bucketProxy.tryConsumeAndReturnRemaining(1)).thenReturn(probe);
            when(request.getHeader("X-Forwarded-For")).thenReturn(null);
            when(request.getRemoteAddr()).thenReturn("10.0.0.5");

            assertThat(interceptor.preHandle(request, response, new Object())).isTrue();
            verify(bucketBuilder).build(eq("llm-rate-limit:10.0.0.5"), any(Supplier.class));
        }

        @Test
        @DisplayName("X-Forwarded-For varsa ilk değer client kimliği olur")
        void xForwardedFor_takesFirstIp() throws Exception {
            ConsumptionProbe probe = consumed(true, 0, 0);
            when(bucketProxy.tryConsumeAndReturnRemaining(1)).thenReturn(probe);
            when(request.getHeader("X-Forwarded-For")).thenReturn("203.0.113.1, 10.0.0.5");

            assertThat(interceptor.preHandle(request, response, new Object())).isTrue();
            verify(bucketBuilder).build(eq("llm-rate-limit:203.0.113.1"), any(Supplier.class));
        }

        @Test
        @DisplayName("Token tükenince 429 döner ve Retry-After header set edilir")
        void tokenExhausted_returns429() throws Exception {
            ConsumptionProbe probe = consumed(false, 0, 7L * 1_000_000_000L);
            when(bucketProxy.tryConsumeAndReturnRemaining(1)).thenReturn(probe);
            when(request.getHeader("X-Forwarded-For")).thenReturn(null);
            when(request.getRemoteAddr()).thenReturn("10.0.0.5");

            StringWriter sw = new StringWriter();
            when(response.getWriter()).thenReturn(new PrintWriter(sw));

            boolean result = interceptor.preHandle(request, response, new Object());

            assertThat(result).isFalse();
            verify(response).setStatus(429);
            verify(response).setHeader(eq("Retry-After"), eq("7"));
            assertThat(sw.toString()).contains("RATE_LIMIT_EXCEEDED");
        }

        @Test
        @DisplayName("Refill için 1 saniyeden az kalsa bile Retry-After en az 1 saniye olur")
        void subSecondRefill_clampsToOneSecond() throws Exception {
            ConsumptionProbe probe = consumed(false, 0, 250_000_000L); // 0.25s
            when(bucketProxy.tryConsumeAndReturnRemaining(1)).thenReturn(probe);
            when(request.getHeader("X-Forwarded-For")).thenReturn(null);
            when(request.getRemoteAddr()).thenReturn("10.0.0.5");

            StringWriter sw = new StringWriter();
            when(response.getWriter()).thenReturn(new PrintWriter(sw));

            interceptor.preHandle(request, response, new Object());

            verify(response).setHeader(eq("Retry-After"), eq("1"));
        }

        @Test
        @DisplayName("Properties'ten gelen yeni limit konfigürasyonu bucket'a iletilir")
        @SuppressWarnings("unchecked")
        void propertiesPropagatedToBucketConfig() throws Exception {
            properties.setMaxRequests(5);
            properties.setDurationSeconds(60);
            ConsumptionProbe probe = consumed(true, 4, 0);
            when(bucketProxy.tryConsumeAndReturnRemaining(1)).thenReturn(probe);
            when(request.getHeader("X-Forwarded-For")).thenReturn(null);
            when(request.getRemoteAddr()).thenReturn("10.0.0.5");

            org.mockito.ArgumentCaptor<Supplier<BucketConfiguration>> captor =
                    org.mockito.ArgumentCaptor.forClass(Supplier.class);
            interceptor.preHandle(request, response, new Object());

            verify(bucketBuilder).build(anyString(), captor.capture());
            BucketConfiguration cfg = captor.getValue().get();
            assertThat(cfg.getBandwidths()).hasSize(1);
            assertThat(cfg.getBandwidths()[0].getCapacity()).isEqualTo(5);
        }
    }
}
