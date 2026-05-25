package com.ticketsystem.it_service_backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Response returned when a new user has been successfully created.
 *
 * <p>The Keycloak UUID and assigned roles of the newly created user are
 * passed back to the frontend so list refresh or detail view operations
 * can happen without an extra round trip.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Kullanıcı oluşturma işlemi başarılı yanıtı")
public class UserCreationResponseDTO {

    @Schema(description = "Keycloak'ta oluşturulan kullanıcının UUID'si",
            example = "a1b2c3d4-e5f6-7890-abcd-ef1234567890")
    private String keycloakId;

    @Schema(description = "Kullanıcı adı", example = "john.doe")
    private String username;

    @Schema(description = "E-posta adresi", example = "john.doe@example.com")
    private String email;

    @Schema(description = "Ad ve soyaddan oluşan tam isim", example = "John Doe")
    private String fullName;

    @Schema(description = "Atanan realm rolleri", example = "[\"AGENT\"]")
    private List<String> assignedRoles;
}
