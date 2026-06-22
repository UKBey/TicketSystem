package com.ticketsystem.it_service_backend.dto;

import com.ticketsystem.it_service_backend.entity.TicketAuditLog;
import com.ticketsystem.it_service_backend.util.AssignAuditNote;
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
 * Audit-log entry for an action performed on a ticket (claim/unclaim/close).
 * Surfaced to the UI through the {@link TicketResponseDTO#getAuditLogs()} list.
 */
@Schema(description = "Bilet denetim günlüğü kaydı — bir aksiyonun detayı")
public class TicketAuditLogDTO {

    @Schema(description = "Denetim kaydının benzersiz kimliği", example = "1")
    private Long id;

    @Schema(description = "İşlemi gerçekleştiren ajanın/kullanıcının Keycloak ID'si")
    private String actorId;

    @Schema(description = "İşlemi gerçekleştiren ajanın/kullanıcının tam adı")
    private String actorName;

    @Schema(description = "ASSIGN aksiyonunda biletin atandığı (hedef) ajanın tam adı")
    private String targetName;

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
        return fromEntity(auditLog, null, null);
    }

    public static TicketAuditLogDTO fromEntity(TicketAuditLog auditLog, String actorName) {
        return fromEntity(auditLog, actorName, null);
    }

    /**
     * Builds the DTO with resolved actor and assignment-target names. The
     * {@code [[assignee:<id>]]} marker is stripped from the note (see
     * {@link AssignAuditNote}); {@code targetName} carries the assignee for ASSIGN rows.
     */
    public static TicketAuditLogDTO fromEntity(TicketAuditLog auditLog, String actorName, String targetName) {
        if (auditLog == null) {
            return null;
        }
        return TicketAuditLogDTO.builder()
                .id(auditLog.getId())
                .actorId(auditLog.getActorId())
                .actorName(actorName)
                .targetName(targetName)
                .actionType(auditLog.getActionType())
                .reasonCode(auditLog.getReasonCode())
                .note(AssignAuditNote.stripMarker(auditLog.getNote()))
                .previousState(auditLog.getPreviousState())
                .newState(auditLog.getNewState())
                .createdAt(auditLog.getCreatedAt())
                .build();
    }
}
