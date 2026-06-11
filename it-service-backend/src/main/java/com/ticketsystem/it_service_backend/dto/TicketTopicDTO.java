package com.ticketsystem.it_service_backend.dto;

import com.ticketsystem.it_service_backend.entity.TicketTopic;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
/**
 * Represents a selectable topic under a product when creating a ticket.
 *
 * <p>Names are bilingual: at least one of {@code nameTr} / {@code nameEn} must be
 * non-blank on create (enforced in the service layer so that isActive-only updates
 * may omit both). Clients display the variant matching the UI language with fallback.
 */
@Schema(description = "Bir ürünün altındaki seçilebilir talep konusu")
public class TicketTopicDTO {

    @Schema(description = "Konunun benzersiz kimliği", example = "12")
    private Long id;

    @Schema(description = "Ait olduğu ürünün kimliği", example = "3")
    private Long productId;

    @Size(max = 255, message = "{validation.topic.name.size}")
    @Schema(description = "Konu adı (Türkçe). nameEn ile en az biri dolu olmalı.", example = "Şifre sıfırlama", nullable = true)
    private String nameTr;

    @Size(max = 255, message = "{validation.topic.name.size}")
    @Schema(description = "Konu adı (İngilizce). nameTr ile en az biri dolu olmalı.", example = "Password reset", nullable = true)
    private String nameEn;

    @Schema(description = "Konu aktif mi (bilet oluştururken görünür mü)", example = "true")
    private Boolean isActive;

    public static TicketTopicDTO fromEntity(TicketTopic topic) {
        return TicketTopicDTO.builder()
                .id(topic.getId())
                .productId(topic.getProductId())
                .nameTr(topic.getNameTr())
                .nameEn(topic.getNameEn())
                .isActive(topic.getIsActive())
                .build();
    }
}
