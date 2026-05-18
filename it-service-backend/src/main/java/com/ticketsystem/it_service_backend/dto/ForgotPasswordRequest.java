package com.ticketsystem.it_service_backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "Şifre sıfırlama bağlantısı talebi")
public class ForgotPasswordRequest {

    @NotBlank
    @Email
    @Size(max = 255)
    @Schema(description = "Bağlantının gönderileceği email", example = "user@example.com")
    private String email;

    /**
     * İstemcinin o anki dili (ISO 639-1). Sağlanırsa mail bu dilde gönderilir;
     * yoksa kullanıcının DB'deki preferred_language değerine düşülür.
     */
    @Pattern(regexp = "^(en|tr)$", message = "must be 'en' or 'tr'")
    @Schema(description = "Mail dilini geçersiz kıl (opsiyonel)", example = "tr")
    private String language;

    /**
     * İstemcinin o anki teması. Sağlanırsa mail bu palette ile basılır;
     * yoksa kullanıcının DB'deki preferred_theme değerine düşülür.
     */
    @Pattern(regexp = "^(light|dark)$", message = "must be 'light' or 'dark'")
    @Schema(description = "Mail temasını geçersiz kıl (opsiyonel)", example = "dark")
    private String theme;
}
