package com.ticketsystem.it_service_backend.dto;

import com.ticketsystem.it_service_backend.entity.TicketWorklog;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import java.time.ZonedDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "İş kaydı (worklog) yanıt modeli")
public class WorklogResponseDTO {

    @Schema(description = "Worklog benzersiz kimliği", example = "15")
    private Long id;

    @Schema(description = "İlgili biletin ID'si", example = "42")
    private Long ticketId;

    @Schema(description = "Kaydı oluşturan ajanın Keycloak ID'si", example = "f9e8d7c6-b5a4-3210-fedc-ba0987654321")
    private String agentId;

    @Schema(description = "Harcanan süre (dakika)", example = "45")
    private Integer minutes;

    @Schema(description = "Yapılan işin açıklaması", example = "Firewall logları incelendi, port kuralları güncellendi.")
    private String description;

    @Schema(description = "Kaydın oluşturulma tarihi", example = "2026-04-21T14:00:00+03:00")
    private ZonedDateTime createdAt;

    @Schema(description = "Son güncelleme tarihi", example = "2026-04-21T14:30:00+03:00")
    private ZonedDateTime updatedAt;

    public static WorklogResponseDTO fromEntity(TicketWorklog worklog) {
        return WorklogResponseDTO.builder()
                .id(worklog.getId())
                .ticketId(worklog.getTicketId())
                .agentId(worklog.getAgentId())
                .minutes(worklog.getMinutes())
                .description(worklog.getDescription())
                .createdAt(worklog.getCreatedAt())
                .updatedAt(worklog.getUpdatedAt())
                .build();
    }
}
