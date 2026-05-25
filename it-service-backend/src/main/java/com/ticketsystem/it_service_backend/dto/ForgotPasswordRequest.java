package com.ticketsystem.it_service_backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
/**
 * Reset-link request for the "forgot password" flow.
 * Bean Validation is enforced; optional {@code language}/{@code theme} fields override the language and palette used in the email.
 */
@Schema(description = "Şifre sıfırlama bağlantısı talebi")
public class ForgotPasswordRequest {

    @NotBlank
    @Email
    @Size(max = 255)
    @Schema(description = "Bağlantının gönderileceği email", example = "user@example.com")
    private String email;

    /**
     * The client's current language (ISO 639-1). When provided, the email is sent in this language;
     * otherwise it falls back to the user's preferred_language stored in the DB.
     */
    @Pattern(regexp = "^(en|tr)$", message = "must be 'en' or 'tr'")
    @Schema(description = "Mail dilini geçersiz kıl (opsiyonel)", example = "tr")
    private String language;

    /**
     * The client's current theme. When provided, the email is rendered with this palette;
     * otherwise it falls back to the user's preferred_theme stored in the DB.
     */
    @Pattern(regexp = "^(light|dark)$", message = "must be 'light' or 'dark'")
    @Schema(description = "Mail temasını geçersiz kıl (opsiyonel)", example = "dark")
    private String theme;
}
