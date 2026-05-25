package com.ticketsystem.it_service_backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
/**
 * User password change request. Current-password verification is performed in the service layer.
 * Bean Validation enforces a minimum length of 8 characters on the new password.
 */
@Schema(description = "Şifre değiştirme isteği")
public class ChangePasswordRequest {

    @NotBlank(message = "{field.notblank}")
    @Schema(description = "Mevcut şifre (doğrulama için)", example = "OldPass123")
    private String currentPassword;

    @NotBlank(message = "{field.notblank}")
    @Size(min = 8, message = "{field.password.size}")
    @Schema(description = "Yeni şifre (min 8 karakter, 1 büyük harf ve 1 rakam içermeli)", example = "NewPass456")
    private String newPassword;
}
