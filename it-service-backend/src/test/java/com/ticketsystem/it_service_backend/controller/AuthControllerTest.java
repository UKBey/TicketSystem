package com.ticketsystem.it_service_backend.controller;

import com.ticketsystem.it_service_backend.dto.ForgotPasswordRequest;
import com.ticketsystem.it_service_backend.dto.ResetPasswordRequest;
import com.ticketsystem.it_service_backend.exception.InvalidPasswordException;
import com.ticketsystem.it_service_backend.service.PasswordResetService;
import com.ticketsystem.it_service_backend.service.PasswordResetService.InvalidResetTokenException;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.distributed.BucketProxy;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.distributed.proxy.RemoteBucketBuilder;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock private PasswordResetService passwordResetService;
    @Mock private ProxyManager<String> bucketProxyManager;
    @Mock private RemoteBucketBuilder<String> bucketBuilder;
    @Mock private BucketProxy bucketProxy;
    @Mock private ConsumptionProbe consumptionProbe;

    private AuthController controller;

    @BeforeEach
    void setUp() {
        controller = new AuthController(passwordResetService, bucketProxyManager);
        ReflectionTestUtils.setField(controller, "forgotMaxRequests", 5);
        ReflectionTestUtils.setField(controller, "forgotWindowSeconds", 3600);
    }

    @SuppressWarnings("unchecked")
    private void bucketAllows(boolean allow) {
        when(bucketProxyManager.builder()).thenReturn(bucketBuilder);
        when(bucketBuilder.build(anyString(), ArgumentMatchers.<Supplier<BucketConfiguration>>any()))
                .thenReturn(bucketProxy);
        when(bucketProxy.tryConsumeAndReturnRemaining(anyLong())).thenReturn(consumptionProbe);
        when(consumptionProbe.isConsumed()).thenReturn(allow);
    }

    private HttpServletRequest requestWithIp(String forwardedFor, String remoteAddr) {
        HttpServletRequest req = mock(HttpServletRequest.class);
        lenient().when(req.getHeader("X-Forwarded-For")).thenReturn(forwardedFor);
        lenient().when(req.getRemoteAddr()).thenReturn(remoteAddr);
        return req;
    }

    @Test
    void forgotPassword_withinLimit_delegatesAndReturnsOk() {
        bucketAllows(true);
        ForgotPasswordRequest body = new ForgotPasswordRequest();
        body.setEmail("a@b.com");
        body.setLanguage("tr");
        body.setTheme("dark");

        ResponseEntity<Map<String, String>> res =
                controller.forgotPassword(body, requestWithIp("203.0.113.7", "10.0.0.1"));

        assertThat(res.getStatusCode().value()).isEqualTo(200);
        assertThat(res.getBody()).containsEntry("status", "ok");
        verify(passwordResetService).requestPasswordReset("a@b.com", "tr", "dark");
    }

    @Test
    void forgotPassword_rateLimited_returns429AndSkipsService() {
        bucketAllows(false);
        ForgotPasswordRequest body = new ForgotPasswordRequest();
        body.setEmail("a@b.com");

        ResponseEntity<Map<String, String>> res =
                controller.forgotPassword(body, requestWithIp(null, "10.0.0.2"));

        assertThat(res.getStatusCode().value()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());
        assertThat(res.getBody()).containsEntry("error", "RATE_LIMIT_EXCEEDED");
        assertThat(res.getHeaders().getFirst("Retry-After")).isEqualTo("3600");
        verify(passwordResetService, never()).requestPasswordReset(anyString(), any(), any());
    }

    @Test
    void forgotPassword_xForwardedForWithCommaList_usesFirstIp() {
        bucketAllows(true);
        ForgotPasswordRequest body = new ForgotPasswordRequest();
        body.setEmail("a@b.com");

        ResponseEntity<Map<String, String>> res =
                controller.forgotPassword(body, requestWithIp("198.51.100.5, 70.41.3.18", "10.0.0.3"));

        assertThat(res.getStatusCode().value()).isEqualTo(200);
    }

    @Test
    void validateResetToken_returnsServiceVerdict() {
        when(passwordResetService.isTokenValid("tok")).thenReturn(true);
        ResponseEntity<Map<String, Boolean>> res = controller.validateResetToken("tok");
        assertThat(res.getBody()).containsEntry("valid", true);
    }

    @Test
    void resetPassword_success_returnsOk() {
        ResetPasswordRequest body = new ResetPasswordRequest();
        body.setToken("tok");
        body.setNewPassword("newpass");

        ResponseEntity<?> res = controller.resetPassword(body);

        assertThat(res.getStatusCode().value()).isEqualTo(200);
        verify(passwordResetService).resetPassword("tok", "newpass", null, null);
    }

    @Test
    void resetPassword_invalidToken_returns400WithCode() {
        ResetPasswordRequest body = new ResetPasswordRequest();
        body.setToken("bad");
        body.setNewPassword("newpass");
        doThrow(new InvalidResetTokenException("expired"))
                .when(passwordResetService).resetPassword(anyString(), anyString(), any(), any());

        ResponseEntity<?> res = controller.resetPassword(body);

        assertThat(res.getStatusCode().value()).isEqualTo(400);
        assertThat((Map<String, Object>) res.getBody()).containsEntry("error", "INVALID_OR_EXPIRED_TOKEN");
    }

    @Test
    void resetPassword_policyViolation_returns400WithDetail() {
        ResetPasswordRequest body = new ResetPasswordRequest();
        body.setToken("tok");
        body.setNewPassword("weak");
        doThrow(new InvalidPasswordException("too short"))
                .when(passwordResetService).resetPassword(anyString(), anyString(), any(), any());

        ResponseEntity<?> res = controller.resetPassword(body);

        assertThat(res.getStatusCode().value()).isEqualTo(400);
        assertThat((Map<String, Object>) res.getBody()).containsEntry("error", "PASSWORD_POLICY_VIOLATION");
    }
}
