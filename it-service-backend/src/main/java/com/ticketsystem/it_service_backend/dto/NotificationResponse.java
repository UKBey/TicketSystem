package com.ticketsystem.it_service_backend.dto;

import com.ticketsystem.it_service_backend.entity.Notification;
import com.ticketsystem.it_service_backend.entity.NotificationType;
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
@Schema(description = "Kullanıcı bildirimi yanıt modeli")
public class NotificationResponse {

    @Schema(description = "Bildirim ID'si", example = "42")
    private Long id;

    @Schema(description = "Alıcı kullanıcının Keycloak ID'si")
    private String userId;

    @Schema(description = "Bildirim mesajı", example = "Bilet #12 atandı.")
    private String message;

    @Schema(description = "Okundu mu?", example = "false")
    private Boolean isRead;

    @Schema(description = "Oluşturulma zamanı")
    private ZonedDateTime createdAt;

    @Schema(description = "Bildirim tipi", example = "TICKET_ASSIGNED")
    private NotificationType type;

    @Schema(description = "İlgili kaydın ID'si (örn. ticket ID)", example = "12")
    private Long referenceId;

    @Schema(description = "İlgili kayıt tipi", example = "TICKET")
    private String referenceType;

    public static NotificationResponse fromEntity(Notification n) {
        return NotificationResponse.builder()
                .id(n.getId())
                .userId(n.getUserId())
                .message(n.getMessage())
                .isRead(n.getIsRead())
                .createdAt(n.getCreatedAt())
                .type(n.getType())
                .referenceId(n.getReferenceId())
                .referenceType(n.getReferenceType())
                .build();
    }
}
