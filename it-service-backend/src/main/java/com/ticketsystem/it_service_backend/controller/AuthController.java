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
 * Anonim auth akışları — şifre sıfırlama. Tüm endpoint'ler permit-list'tedir;
 * yetkilendirme yerine email enumeration koruması + IP başına Bucket4j rate-limit.
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

    @Operation(
            summary = "Şifre sıfırlama linki iste",
            description = "Email kayıtlıysa tek kullanımlık reset linki gönderilir. "
                    + "Email enumeration'ı önlemek için, email kayıtlı olmasa bile "
                    + "her zaman 200 döner. IP başına saatte 5 istek ile sınırlıdır."
    )
    @PostMapping("/forgot-password")
    public ResponseEntity<Map<String, String>> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest body,
            HttpServletRequest request) {

        String ip = clientIp(request);
        if (!consumeForgotPasswordBucket(ip)) {
            return tooManyRequests(ip);
        }

        passwordResetService.requestPasswordReset(body.getEmail(), body.getLanguage(), body.getTheme());
        return ResponseEntity.ok(Map.of("status", "ok"));
    }

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
     * IP başına Bucket4j ile rate-limit. Mevcut JWT-tabanlı RateLimitInterceptor
     * anonim endpoint'leri atlar; bu yüzden burada manuel bucket çalıştırılır.
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
