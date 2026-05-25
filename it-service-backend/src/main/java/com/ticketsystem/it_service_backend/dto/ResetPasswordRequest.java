package com.ticketsystem.it_service_backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
/**
 * Request to reset the password using the one-time token delivered by email.
 * Bean Validation is enforced (token length, password min 8); optional {@code language}/{@code theme} affect the confirmation email.
 */
@Schema(description = "Token ile şifre sıfırlama isteği")
public class ResetPasswordRequest {

    @NotBlank
    @Size(min = 16, max = 512)
    @Schema(description = "Mail ile gelen tek kullanımlık token")
    private String token;

    @NotBlank
    @Size(min = 8, max = 128)
    @Schema(description = "Yeni şifre", example = "S3cret!P@ss")
    private String newPassword;

    /**
     * The client's current language — determines the language of the
     * "password changed" email sent on success. Falls back to the DB preference if absent.
     */
    @Pattern(regexp = "^(en|tr)$", message = "must be 'en' or 'tr'")
    @Schema(description = "Onay mailinin dili (opsiyonel)", example = "tr")
    private String language;

    /**
     * The client's current theme — determines the palette of the
     * "password changed" email sent on success. Falls back to the DB preference if absent.
     */
    @Pattern(regexp = "^(light|dark)$", message = "must be 'light' or 'dark'")
    @Schema(description = "Onay mailinin teması (opsiyonel)", example = "dark")
    private String theme;
}
