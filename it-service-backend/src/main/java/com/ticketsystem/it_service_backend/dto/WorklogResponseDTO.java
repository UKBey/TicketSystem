package com.ticketsystem.it_service_backend.dto;

import com.ticketsystem.it_service_backend.entity.TicketWorklog;
import lombok.*;
import java.time.ZonedDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorklogResponseDTO {

    private Long id;

    private Long ticketId;

    private String agentId;

    private Integer minutes;

    private String description;

    private ZonedDateTime createdAt;

    public static WorklogResponseDTO fromEntity(TicketWorklog worklog) {
        return WorklogResponseDTO.builder()
                .id(worklog.getId())
                .ticketId(worklog.getTicketId())
                .agentId(worklog.getAgentId())
                .minutes(worklog.getMinutes())
                .description(worklog.getDescription())
                .createdAt(worklog.getCreatedAt())
                .build();
    }
}
