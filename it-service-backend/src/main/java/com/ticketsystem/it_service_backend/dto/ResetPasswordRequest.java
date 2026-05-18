package com.ticketsystem.it_service_backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
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
}
