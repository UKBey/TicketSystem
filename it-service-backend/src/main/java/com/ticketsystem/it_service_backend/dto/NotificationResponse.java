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

    /**
     * Maps a notification using its legacy stored {@code message} text as-is.
     * Kept for callers that do not localize; the read path should prefer
     * {@link #fromEntity(Notification, String)} to render in the requester's locale.
     */
    public static NotificationResponse fromEntity(Notification n) {
        return fromEntity(n, n.getMessage());
    }

    /**
     * Maps a notification, using {@code renderedMessage} as the {@code message}
     * field. The read path resolves the recipient's current language, renders the
     * stored message key + args into {@code renderedMessage}, and passes it here so
     * the JSON shape stays identical (a plain {@code message} string).
     */
    public static NotificationResponse fromEntity(Notification n, String renderedMessage) {
        return NotificationResponse.builder()
                .id(n.getId())
                .userId(n.getUserId())
                .message(renderedMessage)
                .isRead(n.getIsRead())
                .createdAt(n.getCreatedAt())
                .type(n.getType())
                .referenceId(n.getReferenceId())
                .referenceType(n.getReferenceType())
                .build();
    }
}
