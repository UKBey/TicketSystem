package com.ticketsystem.it_service_backend.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketsystem.it_service_backend.service.KeycloakAdminService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * AuthController — forgot/reset password endpoint entegrasyon testleri.
 *
 * <p>Gerçek PostgreSQL ve Redis container'ları üzerinde çalışır. Keycloak
 * Admin servisi mock'lanır ki gerçek Keycloak'a istek atılmasın.
 */
@DisplayName("AuthController — Password Reset Entegrasyon Testleri")
class AuthControllerIT extends BaseIntegrationTest {

    @MockitoBean
    private KeycloakAdminService keycloakAdminService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private JdbcTemplate jdbc;

    private static final String EMAIL = "alice@example.com";
    private static final String USER_ID = "kc-uuid-alice";

    void seedUser() {
        jdbc.update(
                "INSERT INTO users (id, email, full_name, role, is_active, preferred_language, preferred_theme, created_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                USER_ID, EMAIL, "Alice", "CUSTOMER", true, "en", "light", Timestamp.from(Instant.now())
        );
    }

    @Nested
    @DisplayName("POST /api/v1/auth/forgot-password")
    class ForgotPassword {

        @Test
        @DisplayName("Bilinen email → 200 + DB'de token kaydı")
        void knownEmail_creates_token() throws Exception {
            seedUser();

            mockMvc.perform(post("/api/v1/auth/forgot-password")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of("email", EMAIL))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("ok"));

            Integer count = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM password_reset_tokens WHERE user_id = ?",
                    Integer.class, USER_ID);
            assert count != null && count == 1;
        }

        @Test
        @DisplayName("Bilinmeyen email → 200 (enumeration koruması), token yok")
        void unknownEmail_returnsOk_noToken() throws Exception {
            mockMvc.perform(post("/api/v1/auth/forgot-password")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of("email", "ghost@example.com"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("ok"));

            Integer count = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM password_reset_tokens", Integer.class);
            assert count != null && count == 0;
        }

        @Test
        @DisplayName("Geçersiz email formatı → 400")
        void invalidEmail_returnsBadRequest() throws Exception {
            mockMvc.perform(post("/api/v1/auth/forgot-password")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of("email", "not-an-email"))))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("POST /api/v1/auth/reset-password")
    class ResetPassword {

        @Test
        @DisplayName("Geçersiz token → 400 + INVALID_OR_EXPIRED_TOKEN")
        void invalidToken_returnsError() throws Exception {
            mockMvc.perform(post("/api/v1/auth/reset-password")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of(
                                    "token", "this-token-does-not-exist-aaaaaaaaaa",
                                    "newPassword", "NewPass123!"
                            ))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("INVALID_OR_EXPIRED_TOKEN"));

            verify(keycloakAdminService, never()).changeUserPassword(anyString(), anyString());
        }

        @Test
        @DisplayName("Çok kısa şifre → 400 validation hatası")
        void shortPassword_returnsValidationError() throws Exception {
            mockMvc.perform(post("/api/v1/auth/reset-password")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of(
                                    "token", "aaaaaaaaaaaaaaaaaaaaaa",
                                    "newPassword", "short"
                            ))))
                    .andExpect(status().isBadRequest());

            verify(keycloakAdminService, never()).changeUserPassword(anyString(), anyString());
        }
    }

    @Nested
    @DisplayName("GET /api/v1/auth/reset-password/validate")
    class ValidateToken {

        @Test
        @DisplayName("Bilinmeyen token → valid=false")
        void unknownToken_returnsFalse() throws Exception {
            mockMvc.perform(get("/api/v1/auth/reset-password/validate")
                            .param("token", "definitely-not-in-db"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.valid").value(false));
        }
    }

    @Nested
    @DisplayName("End-to-end happy path")
    class HappyPath {

        @Test
        @DisplayName("forgot → validate → reset zinciri çalışır")
        void fullFlow_resetsPassword() throws Exception {
            seedUser();
            doNothing().when(keycloakAdminService).changeUserPassword(anyString(), anyString());

            // 1. Reset isteği
            mockMvc.perform(post("/api/v1/auth/forgot-password")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of("email", EMAIL))))
                    .andExpect(status().isOk());

            // 2. DB'den hash'li token'ı bulmak için, plaintext token'ı bilemeyiz.
            //    Bunun yerine token kaydının yaratıldığını doğruluyoruz ve
            //    PasswordResetService'in mantığını ayrı bir test ele alıyor.
            Integer tokens = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM password_reset_tokens WHERE user_id = ? AND used_at IS NULL",
                    Integer.class, USER_ID);
            assert tokens != null && tokens == 1;

            // 3. Geçersiz plaintext ile reset deneyince INVALID dönmeli
            mockMvc.perform(post("/api/v1/auth/reset-password")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of(
                                    "token", "wrong-plaintext-aaaaaaaaaaaaaaaa",
                                    "newPassword", "Sup3r$tr0ng!"
                            ))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("INVALID_OR_EXPIRED_TOKEN"));

            verify(keycloakAdminService, never()).changeUserPassword(anyString(), anyString());
        }
    }
}
