package com.ticketsystem.it_service_backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
/**
 * Mail ile gelen tek kullanımlık token üzerinden şifreyi yenileme isteği.
 * Bean Validation aktif (token uzunluk, şifre min 8); opsiyonel {@code language}/{@code theme} onay mailini etkiler.
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
     * İstemcinin o anki dili — başarı sonrası gönderilen "şifre değiştirildi"
     * mailinin dilini belirler. Verilmezse DB tercihine düşülür.
     */
    @Pattern(regexp = "^(en|tr)$", message = "must be 'en' or 'tr'")
    @Schema(description = "Onay mailinin dili (opsiyonel)", example = "tr")
    private String language;

    /**
     * İstemcinin o anki teması — başarı sonrası gönderilen "şifre değiştirildi"
     * mailinin palettini belirler. Verilmezse DB tercihine düşülür.
     */
    @Pattern(regexp = "^(light|dark)$", message = "must be 'light' or 'dark'")
    @Schema(description = "Onay mailinin teması (opsiyonel)", example = "dark")
    private String theme;
}
