package com.ticketsystem.it_service_backend.dto;

import com.ticketsystem.it_service_backend.entity.Priority;
import com.ticketsystem.it_service_backend.entity.SlaPolicy;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
/**
 * Response model for the SLA policy configured on a priority level (target hours and warning threshold).
 * Used by the admin management screens and by SlaPolicyService's outward-facing APIs.
 */
@Schema(description = "SLA politikası bilgisi")
public class SlaPolicyResponseDTO {

    @Schema(description = "Kayıt ID", example = "1")
    private Long id;

    @Schema(description = "Öncelik seviyesi", example = "HIGH")
    private Priority priority;

    @Schema(description = "Hedef çözüm süresi (saat)", example = "4")
    private Integer targetResolutionHours;

    @Schema(description = "Uyarı eşiği (saat) — deadline'a bu kadar kala uyarı gönderilir", example = "2")
    private Integer warningThresholdHours;

    public static SlaPolicyResponseDTO fromEntity(SlaPolicy entity) {
        return SlaPolicyResponseDTO.builder()
                .id(entity.getId())
                .priority(entity.getPriority())
                .targetResolutionHours(entity.getTargetResolutionHours())
                .warningThresholdHours(entity.getWarningThresholdHours())
                .build();
    }
}
