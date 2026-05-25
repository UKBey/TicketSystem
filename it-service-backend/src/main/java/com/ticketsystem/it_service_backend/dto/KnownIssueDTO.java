package com.ticketsystem.it_service_backend.dto;

import com.ticketsystem.it_service_backend.entity.KnownIssue;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
/**
 * Bir ürüne (ve opsiyonel olarak bir topic'e) bağlı sıkça karşılaşılan sorun (KnownIssue) kayıt yanıtı / istek modeli.
 * Bean Validation ile {@code title} ve {@code content} alanları (notblank + size) doğrulanır.
 */
@Schema(description = "Bir ürüne (ve opsiyonel olarak bir topic'e) bağlı sıkça karşılaşılan sorun.")
public class KnownIssueDTO {

    @Schema(description = "Kayıt ID'si", example = "42")
    private Long id;

    @Schema(description = "Bağlı olduğu ürün ID'si", example = "3")
    private Long productId;

    @Schema(description = "Opsiyonel: bağlı olduğu talep konusu ID'si", example = "12")
    private Long topicId;

    @NotBlank(message = "{validation.known-issue.title.notblank}")
    @Size(max = 255, message = "{validation.known-issue.title.size}")
    @Schema(description = "Sorunun başlığı", example = "VPN bağlantısı kopuyor")
    private String title;

    @NotBlank(message = "{validation.known-issue.content.notblank}")
    @Size(max = 10000, message = "{validation.known-issue.content.size}")
    @Schema(description = "Sorunun ayrıntıları ve çözüm önerisi", example = "Ağ ayarlarınızı kontrol edip ...")
    private String content;

    @Schema(description = "Aktif mi (kullanıcılara gösterilir mi)", example = "true")
    private Boolean isActive;

    @Schema(description = "Kaydı oluşturan kullanıcının Keycloak ID'si")
    private String createdBy;

    @Schema(description = "Oluşturulma tarihi")
    private OffsetDateTime createdAt;

    @Schema(description = "Son güncellenme tarihi")
    private OffsetDateTime updatedAt;

    public static KnownIssueDTO fromEntity(KnownIssue entity) {
        return KnownIssueDTO.builder()
                .id(entity.getId())
                .productId(entity.getProductId())
                .topicId(entity.getTopicId())
                .title(entity.getTitle())
                .content(entity.getContent())
                .isActive(entity.getIsActive())
                .createdBy(entity.getCreatedBy())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
