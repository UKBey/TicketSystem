package com.ticketsystem.it_service_backend.dto;

import com.ticketsystem.it_service_backend.entity.User;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Kullanıcı bilgi modeli — Keycloak senkronizasyonu sonrası döner")
public class UserDTO {

    @Schema(description = "Keycloak subject ID (UUID)", example = "a1b2c3d4-e5f6-7890-abcd-ef1234567890")
    private String id;

    @Schema(description = "Kullanıcının e-posta adresi", example = "user@example.com")
    private String email;

    @Schema(description = "Kullanıcının tam adı", example = "Ali Yılmaz")
    private String fullName;

    @Schema(description = "Uygulama içi rol", example = "AGENT", allowableValues = {"CUSTOMER", "AGENT", "MANAGER"})
    private String role;

    @Schema(description = "Kullanıcı aktif mi?", example = "true")
    private Boolean isActive;

    @Schema(description = "Kullanıcının tercih ettiği dil kodu (ISO 639-1)", example = "tr", allowableValues = {"en", "tr"})
    private String preferredLanguage;

    @Schema(description = "Kullanıcının tercih ettiği tema", example = "dark", allowableValues = {"light", "dark"})
    private String preferredTheme;

    @Schema(description = "Kullanıcının sisteme ilk kaydedildiği tarih", example = "2026-01-15T09:00:00+03:00")
    private ZonedDateTime createdAt;

    @Schema(description = "Kullanıcının yetkili olduğu ürün/kategori listesi")
    private List<ProductDTO> authorizedProducts;

    public static UserDTO fromEntity(User user) {
        return UserDTO.builder()
                .id(user.getId())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .role(user.getRole())
                .isActive(user.getIsActive())
                .createdAt(user.getCreatedAt())
                .preferredLanguage(user.getPreferredLanguage())
                .preferredTheme(user.getPreferredTheme())
                .authorizedProducts(user.getAuthorizedProducts() != null ? 
                    user.getAuthorizedProducts().stream().map(ProductDTO::fromEntity).collect(Collectors.toList()) : null)
                .build();
    }
}
