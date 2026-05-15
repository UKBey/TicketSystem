package com.ticketsystem.it_service_backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "Profil güncelleme isteği")
public class UpdateProfileRequest {

    @NotBlank(message = "{field.notblank}")
    @Size(max = 50, message = "{field.size}")
    @Schema(description = "Kullanıcının adı", example = "John")
    private String firstName;

    @NotBlank(message = "{field.notblank}")
    @Size(max = 50, message = "{field.size}")
    @Schema(description = "Kullanıcının soyadı", example = "Doe")
    private String lastName;

    @NotBlank(message = "{field.notblank}")
    @Email(message = "{field.email}")
    @Schema(description = "Kullanıcının e-posta adresi", example = "john.doe@example.com")
    private String email;
}
