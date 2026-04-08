package com.ticketsystem.it_service_backend.dto;

import com.ticketsystem.it_service_backend.entity.ResolutionNote;
import lombok.*;

import java.time.ZonedDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResolutionNoteResponseDTO {

    private Long id;

    private Long ticketId;

    private String agentId;

    private String note;

    private ZonedDateTime createdAt;

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
