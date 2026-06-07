package com.ticketsystem.it_service_backend.controller;

import com.ticketsystem.it_service_backend.dto.ForgotPasswordRequest;
import com.ticketsystem.it_service_backend.dto.ResetPasswordRequest;
import com.ticketsystem.it_service_backend.exception.InvalidPasswordException;
import com.ticketsystem.it_service_backend.service.PasswordResetService;
import com.ticketsystem.it_service_backend.service.PasswordResetService.InvalidResetTokenException;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.Refill;
import io.github.bucket4j.distributed.BucketProxy;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.Map;

/**
 * REST controller for anonymous auth flows — password reset.
 *
 * <p>All endpoints are on the permit-list; instead of authorization, email enumeration
 * protection and per-IP Bucket4j rate-limiting are applied. Token generation/validation
 * and the actual Keycloak password change are delegated to {@link PasswordResetService}.
 */
@Log4j2
@Tag(name = "Authentication", description = "Anonim auth akışları (şifre sıfırlama)")
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private static final String FORGOT_BUCKET_PREFIX = "forgot-password:";

    private final PasswordResetService passwordResetService;
    private final ProxyManager<String> bucketProxyManager;

    @Value("${app.password-reset.rate-limit.max-requests:5}")
    private int forgotMaxRequests;

    @Value("${app.password-reset.rate-limit.window-seconds:3600}")
    private int forgotWindowSeconds;

    @Value("${app.password-reset.rate-limit.enabled:true}")
    private boolean forgotRateLimitEnabled;

    /**
     * Queues a password reset email; always returns {@code 200} regardless of the outcome to prevent enumeration.
     *
     * @param body the email address with an optional language/theme preference
     * @param request used to extract the IP that serves as the rate-limit key
     * @return {@code {"status":"ok"}}, or {@code 429} if the rate limit is exceeded
     */
    @Operation(
            summary = "Şifre sıfırlama linki iste",
            description = "Email kayıtlıysa tek kullanımlık reset linki gönderilir. "
                    + "Email enumeration'ı önlemek için, email kayıtlı olmasa bile "
                    + "her zaman 200 döner. IP başına oran sınırlıdır (env ile ayarlanabilir/kapatılabilir)."
    )
    @PostMapping("/forgot-password")
    public ResponseEntity<Map<String, String>> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest body,
            HttpServletRequest request) {

        String ip = clientIp(request);
        if (forgotRateLimitEnabled && !consumeForgotPasswordBucket(ip)) {
            return tooManyRequests(ip);
        }

        passwordResetService.requestPasswordReset(body.getEmail(), body.getLanguage(), body.getTheme());
        return ResponseEntity.ok(Map.of("status", "ok"));
    }

    /**
     * Checks whether a reset token is still valid (unused and not expired).
     *
     * @param token the reset token from the email link
     * @return single-key response of the form {@code {"valid": true|false}}
     */
    @Operation(
            summary = "Reset token geçerli mi?",
            description = "Frontend reset sayfası açıldığında, kullanıcıya parola "
                    + "alanını göstermeden önce token geçerliliğini kontrol eder."
    )
    @GetMapping("/reset-password/validate")
    public ResponseEntity<Map<String, Boolean>> validateResetToken(@RequestParam String token) {
        boolean valid = passwordResetService.isTokenValid(token);
        return ResponseEntity.ok(Map.of("valid", valid));
    }

    /**
     * Resets the Keycloak password using a single-use token; the token becomes invalid on a successful reset.
     *
     * @param body the token, new password and optional language/theme preference
     * @return {@code {"status":"ok"}} on success; {@code 400} with an error code on an invalid token or policy violation
     */
    @Operation(
            summary = "Token ile şifre sıfırla",
            description = "Geçerli bir token ve yeni parola ile Keycloak'taki şifre "
                    + "güncellenir. Token tek kullanımlıktır; başarılı işlemden sonra geçersizleşir."
    )
    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(@Valid @RequestBody ResetPasswordRequest body) {
        try {
            passwordResetService.resetPassword(
                    body.getToken(), body.getNewPassword(),
                    body.getLanguage(), body.getTheme());
            return ResponseEntity.ok(Map.of("status", "ok"));
        } catch (InvalidResetTokenException e) {
            log.info("Reset token reddedildi: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "INVALID_OR_EXPIRED_TOKEN"));
        } catch (InvalidPasswordException e) {
            log.info("Şifre politikası ihlali: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "PASSWORD_POLICY_VIOLATION", "detail", e.getMessage()));
        }
    }

    // ------------------------------------------------------------------
    // Private helpers
    // ------------------------------------------------------------------

    /**
     * Per-IP rate-limit with Bucket4j. The existing JWT-based RateLimitInterceptor
     * skips anonymous endpoints, so a manual bucket is run here.
     */
    private boolean consumeForgotPasswordBucket(String ip) {
        BucketProxy bucket = bucketProxyManager.builder().build(
                FORGOT_BUCKET_PREFIX + ip,
                () -> BucketConfiguration.builder()
                        .addLimit(Bandwidth.classic(
                                forgotMaxRequests,
                                Refill.intervally(forgotMaxRequests, Duration.ofSeconds(forgotWindowSeconds))
                        ))
                        .build()
        );
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
        return probe.isConsumed();
    }

    private ResponseEntity<Map<String, String>> tooManyRequests(String ip) {
        log.warn("Forgot-password rate limit aşıldı. IP: {}", ip);
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header("Retry-After", String.valueOf(forgotWindowSeconds))
                .body(Map.of("error", "RATE_LIMIT_EXCEEDED"));
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            int comma = forwarded.indexOf(',');
            return (comma > 0 ? forwarded.substring(0, comma) : forwarded).trim();
        }
        return request.getRemoteAddr();
    }
}
