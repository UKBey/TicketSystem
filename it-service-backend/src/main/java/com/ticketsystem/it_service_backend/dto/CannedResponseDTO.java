package com.ticketsystem.it_service_backend.dto;

import com.ticketsystem.it_service_backend.entity.CannedResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/**
 * Request / response model for a {@link CannedResponse}. Bean Validation guards the basic
 * field shapes; cross-field rules (at least one content variant, scope/visibility values,
 * role-based scope) are enforced in the service layer.
 *
 * <p>{@code favorite} is computed per requesting user and is output-only; {@code ownerAgentId},
 * {@code createdAt} and {@code updatedAt} are also server-managed.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Yorum composer'ına eklenebilen yeniden kullanılabilir hazır yanıt şablonu.")
public class CannedResponseDTO {

    @Schema(description = "Kayıt ID'si", example = "42")
    private Long id;

    @NotBlank(message = "{validation.cannedResponse.title.notblank}")
    @Size(max = 150, message = "{validation.cannedResponse.title.size}")
    @Schema(description = "Şablonun yönetim/arama başlığı (dilden bağımsız)", example = "VPN bağlantı adımları")
    private String title;

    @Size(max = 50, message = "{validation.cannedResponse.shortcut.size}")
    @Schema(description = "Composer'da `/` sonrası yazılan kısayol (slash'sız, küçük harf)", example = "vpn")
    private String shortcut;

    @Size(max = 2000, message = "{validation.cannedResponse.content.size}")
    @Schema(description = "Türkçe içerik varyantı (placeholder'lar ham saklanır)")
    private String contentTr;

    @Size(max = 2000, message = "{validation.cannedResponse.content.size}")
    @Schema(description = "İngilizce içerik varyantı")
    private String contentEn;

    @Schema(description = "Kapsam", example = "PERSONAL", allowableValues = {"PERSONAL", "SHARED"})
    private String scope;

    @Schema(description = "Sahip/oluşturan ajanın Keycloak ID'si (sunucu yönetir)")
    private String ownerAgentId;

    @Schema(description = "Opsiyonel ürün bağlantısı — yalnız SHARED için anlamlı; null ise global")
    private Long productId;

    @Schema(description = "Görünürlük", example = "BOTH", allowableValues = {"EXTERNAL", "INTERNAL", "BOTH"})
    private String visibility;

    @Schema(description = "İstekte bulunan kullanıcı bu şablonu favorilemiş mi", example = "true")
    private Boolean favorite;

    @Schema(description = "Oluşturulma tarihi")
    private OffsetDateTime createdAt;

    @Schema(description = "Son güncellenme tarihi")
    private OffsetDateTime updatedAt;

    public static CannedResponseDTO fromEntity(CannedResponse e) {
        return fromEntity(e, false);
    }

    public static CannedResponseDTO fromEntity(CannedResponse e, boolean favorite) {
        return CannedResponseDTO.builder()
                .id(e.getId())
                .title(e.getTitle())
                .shortcut(e.getShortcut())
                .contentTr(e.getContentTr())
                .contentEn(e.getContentEn())
                .scope(e.getScope() == null ? null : e.getScope().name())
                .ownerAgentId(e.getOwnerAgentId())
                .productId(e.getProductId())
                .visibility(e.getVisibility() == null ? null : e.getVisibility().name())
                .favorite(favorite)
                .createdAt(e.getCreatedAt())
                .updatedAt(e.getUpdatedAt())
                .build();
    }
}
