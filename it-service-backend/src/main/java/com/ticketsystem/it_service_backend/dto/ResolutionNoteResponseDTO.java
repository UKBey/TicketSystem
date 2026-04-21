package com.ticketsystem.it_service_backend.dto;

import com.ticketsystem.it_service_backend.entity.ResolutionNote;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import java.time.ZonedDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Çözüm notu yanıt modeli — bilet başına en fazla bir adet olabilir")
public class ResolutionNoteResponseDTO {

    @Schema(description = "Çözüm notunun benzersiz kimliği", example = "7")
    private Long id;

    @Schema(description = "İlgili biletin ID'si", example = "42")
    private Long ticketId;

    @Schema(description = "Notu yazan ajanın Keycloak ID'si", example = "f9e8d7c6-b5a4-3210-fedc-ba0987654321")
    private String agentId;

    @Schema(description = "Çözüm açıklaması", example = "DNS ayarları düzeltildi, client tarafında cache temizlendi.")
    private String note;

    @Schema(description = "Notun oluşturulma tarihi", example = "2026-04-21T15:30:00+03:00")
    private ZonedDateTime createdAt;

    @Schema(description = "Son güncelleme tarihi (ilk oluşturmada null)", example = "2026-04-21T16:00:00+03:00")
    private ZonedDateTime updatedAt;

    public static ResolutionNoteResponseDTO fromEntity(ResolutionNote resolutionNote) {
        return ResolutionNoteResponseDTO.builder()
                .id(resolutionNote.getId())
                .ticketId(resolutionNote.getTicketId())
                .agentId(resolutionNote.getAgentId())
                .note(resolutionNote.getNote())
                .createdAt(resolutionNote.getCreatedAt())
                .updatedAt(resolutionNote.getUpdatedAt())
                .build();
    }
}
