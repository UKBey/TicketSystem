package com.ticketsystem.it_service_backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
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
}
