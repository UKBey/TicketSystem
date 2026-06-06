package com.ticketsystem.it_service_backend.service;

import com.ticketsystem.it_service_backend.entity.PasswordResetToken;
import com.ticketsystem.it_service_backend.entity.User;
import com.ticketsystem.it_service_backend.exception.InvalidPasswordException;
import com.ticketsystem.it_service_backend.repository.PasswordResetTokenRepository;
import com.ticketsystem.it_service_backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.ZonedDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PasswordResetServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordResetTokenRepository tokenRepository;
    @Mock private EmailService emailService;
    @Mock private KeycloakAdminService keycloakAdminService;

    private PasswordResetService service;

    @BeforeEach
    void setUp() {
        service = new PasswordResetService(userRepository, tokenRepository, emailService, keycloakAdminService);
        ReflectionTestUtils.setField(service, "tokenTtlMinutes", 60);
        ReflectionTestUtils.setField(service, "frontendBaseUrl", "http://localhost/");
    }

    private User activeUser() {
        return User.builder().id("u-1").email("a@b.com").isActive(true).build();
    }

    // ---- requestPasswordReset ----

    @Test
    void requestPasswordReset_blankEmail_silentlyReturns() {
        service.requestPasswordReset("  ", null, null);
        verify(userRepository, never()).findByEmailIgnoreCase(anyString());
        verify(tokenRepository, never()).save(any());
    }

    @Test
    void requestPasswordReset_unknownEmail_silentlyReturns() {
        when(userRepository.findByEmailIgnoreCase("x@y.com")).thenReturn(Optional.empty());
        service.requestPasswordReset("x@y.com", null, null);
        verify(tokenRepository, never()).save(any());
        verify(emailService, never()).sendPasswordResetEmail(any(), anyString(), anyInt(), any(), any());
    }

    @Test
    void requestPasswordReset_inactiveUser_silentlyReturns() {
        User inactive = User.builder().id("u-2").email("a@b.com").isActive(false).build();
        when(userRepository.findByEmailIgnoreCase("a@b.com")).thenReturn(Optional.of(inactive));
        service.requestPasswordReset("a@b.com", null, null);
        verify(tokenRepository, never()).save(any());
    }

    @Test
    void requestPasswordReset_happyPath_invalidatesSavesAndEmails() {
        when(userRepository.findByEmailIgnoreCase("a@b.com")).thenReturn(Optional.of(activeUser()));

        service.requestPasswordReset(" a@b.com ", "tr", "dark");

        verify(tokenRepository).invalidateActiveTokensForUser(eqUser(), any(ZonedDateTime.class));
        ArgumentCaptor<PasswordResetToken> captor = ArgumentCaptor.forClass(PasswordResetToken.class);
        verify(tokenRepository).save(captor.capture());
        assertThat(captor.getValue().getTokenHash()).isNotBlank();
        // buildResetUrl: trailing slash temizlenir, token query param eklenir.
        ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendPasswordResetEmail(any(User.class), urlCaptor.capture(), eqTtl(), any(), any());
        assertThat(urlCaptor.getValue()).startsWith("http://localhost/reset-password?token=");
        assertThat(urlCaptor.getValue()).doesNotContain("localhost//reset");
    }

    private static String eqUser() { return org.mockito.ArgumentMatchers.eq("u-1"); }
    private static int eqTtl() { return org.mockito.ArgumentMatchers.eq(60); }
    private static int anyInt() { return org.mockito.ArgumentMatchers.anyInt(); }

    // ---- isTokenValid ----

    @Test
    void isTokenValid_blank_false() {
        assertFalse(service.isTokenValid("   "));
        verify(tokenRepository, never()).findByTokenHash(anyString());
    }

    @Test
    void isTokenValid_validToken_true() {
        PasswordResetToken token = PasswordResetToken.builder()
                .expiresAt(ZonedDateTime.now().plusMinutes(30)).build();
        when(tokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(token));
        assertTrue(service.isTokenValid("plain"));
    }

    @Test
    void isTokenValid_notFound_false() {
        when(tokenRepository.findByTokenHash(anyString())).thenReturn(Optional.empty());
        assertFalse(service.isTokenValid("plain"));
    }

    // ---- resetPassword ----

    @Test
    void resetPassword_blankToken_throwsInvalidResetToken() {
        assertThrows(PasswordResetService.InvalidResetTokenException.class,
                () -> service.resetPassword("  ", "newpass", null, null));
    }

    @Test
    void resetPassword_blankPassword_throwsInvalidPassword() {
        assertThrows(InvalidPasswordException.class,
                () -> service.resetPassword("token", "  ", null, null));
    }

    @Test
    void resetPassword_tokenNotFound_throwsInvalidResetToken() {
        when(tokenRepository.findByTokenHash(anyString())).thenReturn(Optional.empty());
        assertThrows(PasswordResetService.InvalidResetTokenException.class,
                () -> service.resetPassword("token", "newpass", null, null));
    }

    @Test
    void resetPassword_expiredToken_throwsInvalidResetToken() {
        PasswordResetToken expired = PasswordResetToken.builder()
                .user(activeUser()).expiresAt(ZonedDateTime.now().minusMinutes(1)).build();
        when(tokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(expired));
        assertThrows(PasswordResetService.InvalidResetTokenException.class,
                () -> service.resetPassword("token", "newpass", null, null));
        verify(keycloakAdminService, never()).changeUserPassword(anyString(), anyString());
    }

    @Test
    void resetPassword_happyPath_changesPasswordMarksUsedAndEmails() {
        PasswordResetToken valid = PasswordResetToken.builder()
                .user(activeUser()).expiresAt(ZonedDateTime.now().plusMinutes(30)).build();
        when(tokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(valid));

        service.resetPassword("token", "newpass", "en", "light");

        verify(keycloakAdminService).changeUserPassword("u-1", "newpass");
        assertThat(valid.getUsedAt()).isNotNull();
        verify(tokenRepository).save(valid);
        verify(emailService).sendPasswordChangedEmail(any(User.class), org.mockito.ArgumentMatchers.eq("en"), org.mockito.ArgumentMatchers.eq("light"));
    }
}
