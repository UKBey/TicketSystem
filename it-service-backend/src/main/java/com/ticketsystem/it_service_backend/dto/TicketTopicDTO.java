package com.ticketsystem.it_service_backend.dto;

import com.ticketsystem.it_service_backend.entity.TicketTopic;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
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
 * Bean Validation enforces the {@code name} field (notblank + size).
 */
@Schema(description = "Bir ürünün altındaki seçilebilir talep konusu")
public class TicketTopicDTO {

    @Schema(description = "Konunun benzersiz kimliği", example = "12")
    private Long id;

    @Schema(description = "Ait olduğu ürünün kimliği", example = "3")
    private Long productId;

    @NotBlank(message = "{validation.topic.name.notblank}")
    @Size(max = 255, message = "{validation.topic.name.size}")
    @Schema(description = "Konu adı", example = "Şifre sıfırlama")
    private String name;

    @Schema(description = "Konu aktif mi (bilet oluştururken görünür mü)", example = "true")
    private Boolean isActive;

    public static TicketTopicDTO fromEntity(TicketTopic topic) {
        return TicketTopicDTO.builder()
                .id(topic.getId())
                .productId(topic.getProductId())
                .name(topic.getName())
                .isActive(topic.getIsActive())
                .build();
    }
}
