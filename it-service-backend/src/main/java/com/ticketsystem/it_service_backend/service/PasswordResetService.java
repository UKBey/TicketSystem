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
 * "Forgot password" akışını yönetir. Token üretimi, mail tetiği, validate ve
 * consume tek yerden geçer. Plaintext token sadece mail içinde uçar; DB'de
 * yalnızca SHA-256 hash saklanır.
 *
 * <p>Token tüketilene kadar kullanıcının mevcut şifresi değişmez; sadece
 * resetPassword başarıyla çalışırsa Keycloak'taki şifre güncellenir.
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
     * Verilen email için reset token üretir ve maille gönderir. Email enumeration'ı
     * önlemek için, email kayıtlı olmasa veya kullanıcı pasif olsa bile sessiz
     * şekilde başarılı döner — çağıran taraf hep aynı yanıtı vermelidir.
     *
     * @param languageOverride istemcinin o anki dili (en/tr); null ise DB tercihine düşülür
     * @param themeOverride    istemcinin o anki teması (light/dark); null ise DB tercihine düşülür
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
     * Token'ın hâlâ geçerli olup olmadığını sorgular. Frontend reset sayfasını
     * açtığında, kullanıcıdan parola istemeden önce çağrılır.
     */
    @Transactional(readOnly = true)
    public boolean isTokenValid(String plainToken) {
        if (plainToken == null || plainToken.isBlank()) return false;
        return tokenRepository.findByTokenHash(sha256(plainToken))
                .map(PasswordResetToken::isValid)
                .orElse(false);
    }

    /**
     * Token'ı tüketir ve Keycloak'taki şifreyi günceller. Token geçersizse veya
     * süresi dolmuşsa {@link InvalidResetTokenException} fırlatılır; şifre politikası
     * ihlali için {@link InvalidPasswordException} Keycloak servisinden propage edilir.
     *
     * <p>Şifre değişimi Keycloak'a kadar başarıyla gidene kadar token "used" olarak
     * işaretlenmez — böylece Keycloak hatasında kullanıcı aynı linki tekrar deneyebilir.
     *
     * @param languageOverride başarı mailinin dili (opsiyonel)
     * @param themeOverride    başarı mailinin teması (opsiyonel)
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
