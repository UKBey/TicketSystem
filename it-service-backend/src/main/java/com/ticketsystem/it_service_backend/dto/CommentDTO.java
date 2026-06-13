package com.ticketsystem.it_service_backend.dto;

import com.ticketsystem.it_service_backend.entity.Comment;
import com.ticketsystem.it_service_backend.entity.CommentType;
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
/**
 * Response model for a ticket comment — covers both EXTERNAL (visible to customer) and INTERNAL (agent/manager note) types.
 * Listed in the chat thread on the ticket detail screen; author name/role are resolved server-side.
 */
@Schema(description = "Bilet yorumu yanıt modeli — hem müşteri yanıtı hem dahili notları kapsar")
public class CommentDTO {

    @Schema(description = "Yorumun benzersiz kimliği", example = "128")
    private Long id;

    @Schema(description = "Yorumu yazan kullanıcının Keycloak ID'si", example = "a1b2c3d4-e5f6-7890-abcd-ef1234567890")
    private String authorId;

    @Schema(description = "Yazar adı (sunucu tarafında çözümlenir)", example = "Ali Yılmaz")
    private String authorName;

    @Schema(description = "Yazarın sistemdeki rolü (CUSTOMER/AGENT/LEAD_AGENT/ADMIN/MANAGER) — chat UI'da rozet için kullanılır", example = "AGENT")
    private String authorRole;

    @Schema(description = "Yorum içeriği", example = "VPN ayarlarınızı kontrol ettim, port 443 engelli görünüyor.")
    private String message;

    @Schema(description = "Yorum tipi: EXTERNAL = müşteriye görünür, INTERNAL = sadece agent/manager görebilir", example = "EXTERNAL")
    private CommentType type;

    @Schema(description = "Yorumun oluşturulma tarihi", example = "2026-04-21T11:30:00+03:00")
    private ZonedDateTime createdAt;

    public static CommentDTO fromEntity(Comment comment, String authorName, String authorRole) {
        return CommentDTO.builder()
                .id(comment.getId())
                .authorId(comment.getAuthorId())
                .authorName(authorName)
                .authorRole(authorRole)
                .message(comment.getMessage())
                .type(comment.getType())
                .createdAt(comment.getCreatedAt())
                .build();
    }
}
