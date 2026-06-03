package com.ticketsystem.it_service_backend.dto;

import com.ticketsystem.it_service_backend.entity.User;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
/**
 * User response model, derived from the {@link com.ticketsystem.it_service_backend.entity.User}
 * record created on the backend after Keycloak sync.
 * Carries the role, language/theme preferences and the list of authorized products.
 */
@Schema(description = "Kullanıcı bilgi modeli — Keycloak senkronizasyonu sonrası döner")
public class UserDTO {

    @Schema(description = "Keycloak subject ID (UUID)", example = "a1b2c3d4-e5f6-7890-abcd-ef1234567890")
    private String id;

    @Schema(description = "Kullanıcının e-posta adresi", example = "user@example.com")
    private String email;

    @Schema(description = "Kullanıcının tam adı", example = "Ali Yılmaz")
    private String fullName;

    @Schema(description = "Birincil/gösterim rolü — sahip olunan roller içinde en yüksek öncelikli olan",
            example = "AGENT", allowableValues = {"CUSTOMER", "AGENT", "LEAD_AGENT", "ADMIN", "MANAGER"})
    private String role;

    @Schema(description = "Kullanıcının sahip olduğu TÜM uygulama rolleri (öncelik sırasına göre). " +
            "Etkin yetki bu kümenin birleşimidir.",
            example = "[\"ADMIN\", \"AGENT\"]")
    private List<String> roles;

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

    /** Gösterim önceliği: ADMIN > MANAGER > LEAD_AGENT > AGENT > CUSTOMER. */
    private static final List<String> ROLE_DISPLAY_ORDER =
            List.of("ADMIN", "MANAGER", "LEAD_AGENT", "AGENT", "CUSTOMER");

    /**
     * Kullanıcının rol kümesini gösterim önceliğine göre sıralı bir listeye çevirir.
     * Bilinmeyen/legacy roller (ör. AGENT_ADMIN) bilinenlerden sonra alfabetik eklenir.
     */
    private static List<String> orderRoles(Set<String> roles) {
        if (roles == null || roles.isEmpty()) {
            return List.of();
        }
        List<String> ordered = new ArrayList<>();
        for (String r : ROLE_DISPLAY_ORDER) {
            if (roles.contains(r)) {
                ordered.add(r);
            }
        }
        roles.stream()
                .filter(r -> !ROLE_DISPLAY_ORDER.contains(r))
                .sorted()
                .forEach(ordered::add);
        return ordered;
    }

    public static UserDTO fromEntity(User user) {
        return UserDTO.builder()
                .id(user.getId())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .role(user.getRole())
                .roles(orderRoles(user.getRoles()))
                .isActive(user.getIsActive())
                .createdAt(user.getCreatedAt())
                .preferredLanguage(user.getPreferredLanguage())
                .preferredTheme(user.getPreferredTheme())
                .authorizedProducts(user.getAuthorizedProducts() != null ? 
                    user.getAuthorizedProducts().stream().map(ProductDTO::fromEntity).collect(Collectors.toList()) : null)
                .build();
    }
}
