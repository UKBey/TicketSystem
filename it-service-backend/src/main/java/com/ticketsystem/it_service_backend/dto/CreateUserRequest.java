package com.ticketsystem.it_service_backend.dto;

import lombok.Data;

import java.util.List;

/**
 * Yeni kullanıcı oluşturma isteği için DTO.
 *
 * <p>Bean Validation anotasyonları Commit 5'te eklenecektir.
 * Bu sınıf şimdilik {@link com.ticketsystem.it_service_backend.service.KeycloakAdminService}
 * tarafından derleme bağımlılığı olarak kullanılmaktadır.
 */
@Data
public class CreateUserRequest {

    private String username;
    private String email;
    private String firstName;
    private String lastName;
    private String password;
    private List<String> roles;
}
