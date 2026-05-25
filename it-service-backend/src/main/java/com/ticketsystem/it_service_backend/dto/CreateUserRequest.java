package com.ticketsystem.it_service_backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * DTO for creating a new user.
 *
 * <p>All fields are required. {@code password} must be at least 8 characters;
 * strength checks (uppercase letter + digit) are also enforced on the frontend.
 * The {@code roles} list must contain at least one entry.
 */
@Data
@Schema(description = "Yeni kullanıcı oluşturma isteği")
public class CreateUserRequest {

    @NotBlank(message = "{field.notblank}")
    @Size(min = 3, max = 50, message = "{field.size}")
    @Schema(description = "Keycloak kullanıcı adı (benzersiz)", example = "john.doe", minLength = 3, maxLength = 50)
    private String username;

    @NotBlank(message = "{field.notblank}")
    @Email(message = "{field.email}")
    @Schema(description = "Kullanıcının e-posta adresi (benzersiz)", example = "john.doe@example.com")
    private String email;

    @NotBlank(message = "{field.notblank}")
    @Size(max = 50, message = "{field.size}")
    @Schema(description = "Kullanıcının adı", example = "John")
    private String firstName;

    @NotBlank(message = "{field.notblank}")
    @Size(max = 50, message = "{field.size}")
    @Schema(description = "Kullanıcının soyadı", example = "Doe")
    private String lastName;

    @NotBlank(message = "{field.notblank}")
    @Size(min = 8, message = "{field.password.size}")
    @Schema(description = "Geçici şifre — kullanıcı ilk girişte değiştirmek zorunda kalır (min 8 karakter)",
            example = "Temp1234!", minLength = 8)
    private String password;

    @NotEmpty(message = "{field.required}")
    @Schema(description = "Atanacak realm rolleri — en az bir rol zorunludur",
            example = "[\"AGENT\"]", allowableValues = {"CUSTOMER", "AGENT", "AGENT_ADMIN", "MANAGER"})
    private List<String> roles;

    /**
     * Whether the password is temporary (true → the user must change it on first
     * login) or permanent. Defaults to true. Permanent passwords are only used
     * by automated flows such as the data generator.
     */
    @Schema(description = "Şifre geçici mi? Varsayılan true; data-generator gibi otomasyonlar false geçer.",
            example = "true", defaultValue = "true")
    private Boolean temporaryPassword;
}
