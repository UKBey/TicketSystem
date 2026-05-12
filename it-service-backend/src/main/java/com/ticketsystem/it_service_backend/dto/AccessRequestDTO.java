package com.ticketsystem.it_service_backend.dto;

import com.ticketsystem.it_service_backend.entity.AccessRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Erişim talebi")
public class AccessRequestDTO {

    @Schema(description = "Talep ID'si", example = "1")
    private Long id;

    @Schema(description = "Talebi oluşturan kullanıcının Keycloak ID'si")
    private String userId;

    @Schema(description = "Kullanıcının tam adı")
    private String userFullName;

    @Schema(description = "Kullanıcının e-posta adresi")
    private String userEmail;

    @Schema(description = "Serbest metin talebi")
    private String message;

    @Schema(description = "Talebin oluşturulma tarihi")
    private ZonedDateTime createdAt;

    public static AccessRequestDTO fromEntity(AccessRequest req) {
        return AccessRequestDTO.builder()
                .id(req.getId())
                .userId(req.getUser().getId())
                .userFullName(req.getUser().getFullName())
                .userEmail(req.getUser().getEmail())
                .message(req.getMessage())
                .createdAt(req.getCreatedAt())
                .build();
    }
}
