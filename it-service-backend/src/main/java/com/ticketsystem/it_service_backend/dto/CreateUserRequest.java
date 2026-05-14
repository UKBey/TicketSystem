package com.ticketsystem.it_service_backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * Yeni kullanıcı oluşturma isteği için DTO.
 *
 * <p>Tüm alanlar zorunludur. {@code password} en az 8 karakter olmalıdır;
 * güç kontrolü (büyük harf + rakam) frontend tarafında da uygulanır.
 * {@code roles} listesi en az bir eleman içermelidir.
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
     * Şifre geçici mi (true → kullanıcı ilk girişte değiştirmek zorunda)
     * yoksa kalıcı mı? Varsayılan: true. Yalnızca veri üretici (data-generator)
     * gibi otomatik akışlarda kalıcı şifre tercih edilir.
     */
    @Schema(description = "Şifre geçici mi? Varsayılan true; data-generator gibi otomasyonlar false geçer.",
            example = "true", defaultValue = "true")
    private Boolean temporaryPassword;
}
