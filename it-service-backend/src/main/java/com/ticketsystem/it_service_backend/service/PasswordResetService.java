package com.ticketsystem.it_service_backend.service;

import com.ticketsystem.it_service_backend.entity.PasswordResetToken;
import com.ticketsystem.it_service_backend.entity.User;
import com.ticketsystem.it_service_backend.exception.InvalidPasswordException;
import com.ticketsystem.it_service_backend.repository.PasswordResetTokenRepository;
import com.ticketsystem.it_service_backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.ZonedDateTime;
import java.util.Base64;

/**
 * Drives the "forgot password" flow. Token generation, email delivery,
 * validation and consumption all flow through here. The plaintext token only
 * travels inside the email; the DB stores nothing but the SHA-256 hash.
 *
 * <p>The user's existing password is not changed until the token is consumed;
 * the Keycloak password is updated only on a successful resetPassword call.
 */
@Log4j2
@Service
@RequiredArgsConstructor
public class PasswordResetService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int TOKEN_BYTES = 32; // 256-bit

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final EmailService emailService;
    private final KeycloakAdminService keycloakAdminService;

    @Value("${app.password-reset.token-ttl-minutes:60}")
    private int tokenTtlMinutes;

    @Value("${app.password-reset.frontend-base-url:http://localhost}")
    private String frontendBaseUrl;

    /**
     * Generates a reset token for the given email and emails it out. To prevent
     * email enumeration, the call returns silently with success even if the email
     * is not registered or the user is inactive — the caller must always return
     * the same response.
     *
     * @param email email the reset is being requested for
     * @param languageOverride client's current language (en/tr); falls back to the DB preference when null
     * @param themeOverride    client's current theme (light/dark); falls back to the DB preference when null
     */
    @Transactional
    public void requestPasswordReset(String email, String languageOverride, String themeOverride) {
        if (email == null || email.isBlank()) {
            log.debug("Reset isteği boş email ile geldi, sessiz geçildi");
            return;
        }

        User user = userRepository.findByEmailIgnoreCase(email.trim()).orElse(null);
        if (user == null) {
            log.info("Reset isteği bilinmeyen email için: {} — sessiz başarı döndürülüyor", email);
            return;
        }
        if (Boolean.FALSE.equals(user.getIsActive())) {
            log.info("Reset isteği pasif kullanıcı için: {} — sessiz başarı", user.getId());
            return;
        }

        ZonedDateTime now = ZonedDateTime.now();
        tokenRepository.invalidateActiveTokensForUser(user.getId(), now);

        String plainToken = generatePlainToken();
        String tokenHash = sha256(plainToken);

        PasswordResetToken entity = PasswordResetToken.builder()
                .user(user)
                .tokenHash(tokenHash)
                .createdAt(now)
                .expiresAt(now.plusMinutes(tokenTtlMinutes))
                .build();
        tokenRepository.save(entity);

        String resetUrl = buildResetUrl(plainToken);
        emailService.sendPasswordResetEmail(user, resetUrl, tokenTtlMinutes, languageOverride, themeOverride);
        log.info("Reset linki gönderildi. User: {}, TTL: {} dk, lang={}, theme={}",
                user.getId(), tokenTtlMinutes, languageOverride, themeOverride);
    }

    /**
     * Checks whether the token is still valid. Called by the frontend reset page
     * before prompting the user for a password.
     *
     * @param plainToken plaintext token held by the user
     * @return {@code true} if the token exists and is still valid
     */
    @Transactional(readOnly = true)
    public boolean isTokenValid(String plainToken) {
        if (plainToken == null || plainToken.isBlank()) return false;
        return tokenRepository.findByTokenHash(sha256(plainToken))
                .map(PasswordResetToken::isValid)
                .orElse(false);
    }

    /**
     * Consumes the token and updates the password in Keycloak. When the token is
     * invalid or expired, {@link InvalidResetTokenException} is thrown; password
     * policy violations are propagated as {@link InvalidPasswordException} from the
     * Keycloak service.
     *
     * <p>The token is not marked "used" until the password change succeeds all the
     * way through Keycloak — this lets the user retry with the same link if
     * Keycloak rejects the change.
     *
     * @param plainToken plaintext token held by the user
     * @param newPassword new password (non-blank; must satisfy Keycloak policy)
     * @param languageOverride language for the success email (optional)
     * @param themeOverride    theme for the success email (optional)
     * @throws InvalidResetTokenException if the token is missing/unknown/expired/used
     * @throws InvalidPasswordException if the password is blank or violates Keycloak policy
     */
    @Transactional
    public void resetPassword(String plainToken, String newPassword,
                              String languageOverride, String themeOverride) {
        if (plainToken == null || plainToken.isBlank()) {
            throw new InvalidResetTokenException("Token eksik");
        }
        if (newPassword == null || newPassword.isBlank()) {
            throw new InvalidPasswordException("Şifre boş olamaz");
        }

        PasswordResetToken token = tokenRepository.findByTokenHash(sha256(plainToken))
                .orElseThrow(() -> new InvalidResetTokenException("Token bulunamadı"));

        if (!token.isValid()) {
            throw new InvalidResetTokenException("Token süresi doldu veya kullanıldı");
        }

        User user = token.getUser();
        keycloakAdminService.changeUserPassword(user.getId(), newPassword);

        token.setUsedAt(ZonedDateTime.now());
        tokenRepository.save(token);
        log.info("Şifre başarıyla sıfırlandı. User: {}", user.getId());

        emailService.sendPasswordChangedEmail(user, languageOverride, themeOverride);
    }

    private String generatePlainToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }

    private String buildResetUrl(String plainToken) {
        String base = frontendBaseUrl.endsWith("/")
                ? frontendBaseUrl.substring(0, frontendBaseUrl.length() - 1)
                : frontendBaseUrl;
        return base + "/reset-password?token=" + plainToken;
    }

    public static class InvalidResetTokenException extends RuntimeException {
        public InvalidResetTokenException(String message) {
            super(message);
        }
    }
}
