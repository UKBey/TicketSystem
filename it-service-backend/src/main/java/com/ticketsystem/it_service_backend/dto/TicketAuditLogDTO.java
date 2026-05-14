package com.ticketsystem.it_service_backend.dto;

import com.ticketsystem.it_service_backend.entity.TicketAuditLog;
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
@Schema(description = "Bilet denetim günlüğü kaydı — bir aksiyonun detayı")
public class TicketAuditLogDTO {

    @Schema(description = "Denetim kaydının benzersiz kimliği", example = "1")
    private Long id;

    @Schema(description = "İşlemi gerçekleştiren ajanın/kullanıcının Keycloak ID'si")
    private String actorId;

    @Schema(description = "İşlemi gerçekleştiren ajanın/kullanıcının tam adı")
    private String actorName;

    @Schema(description = "Aksiyon türü", example = "UNCLAIM",
            allowableValues = {"UNCLAIM", "CLOSE", "CLAIM"})
    private String actionType;

    @Schema(description = "Önceden tanımlı sebep kodu (örn. SOLUTION_PROVIDED, WORKLOAD)", example = "WORKLOAD")
    private String reasonCode;

    @Schema(description = "Aksiyonun serbest metin açıklaması (opsiyonel; OTHER seçilince zorunlu)")
    private String note;

    @Schema(description = "Aksiyondan önceki bilet durumu", example = "IN_PROGRESS")
    private String previousState;

    @Schema(description = "Aksiyondan sonraki bilet durumu", example = "NEW")
    private String newState;

    @Schema(description = "Aksiyonun gerçekleştirildiği tarih/saat")
    private ZonedDateTime createdAt;

    public static TicketAuditLogDTO fromEntity(TicketAuditLog auditLog) {
        if (auditLog == null) {
            return null;
        }
        return TicketAuditLogDTO.builder()
                .id(auditLog.getId())
                .actorId(auditLog.getActorId())
                .actionType(auditLog.getActionType())
                .reasonCode(auditLog.getReasonCode())
                .note(auditLog.getNote())
                .previousState(auditLog.getPreviousState())
                .newState(auditLog.getNewState())
                .createdAt(auditLog.getCreatedAt())
                .build();
    }

    public static TicketAuditLogDTO fromEntity(TicketAuditLog auditLog, String actorName) {
        if (auditLog == null) {
            return null;
        }
        return TicketAuditLogDTO.builder()
                .id(auditLog.getId())
                .actorId(auditLog.getActorId())
                .actorName(actorName)
                .actionType(auditLog.getActionType())
                .reasonCode(auditLog.getReasonCode())
                .note(auditLog.getNote())
                .previousState(auditLog.getPreviousState())
                .newState(auditLog.getNewState())
                .createdAt(auditLog.getCreatedAt())
                .build();
    }
}
