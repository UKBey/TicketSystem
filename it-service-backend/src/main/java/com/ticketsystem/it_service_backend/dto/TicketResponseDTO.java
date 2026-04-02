package com.ticketsystem.it_service_backend.dto;

import com.ticketsystem.it_service_backend.entity.Ticket;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.ZonedDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketResponseDTO {
    private Long id;
    private String title;
    private String description;
    private String status;
    private String priority;
    private Long productId;
    private String customerId;
    private String assigneeId;
    private ZonedDateTime slaDeadline;
    private Boolean slaBreached;
    private ZonedDateTime createdAt;
    private ZonedDateTime resolvedAt;
    private ZonedDateTime closedAt;
    private Boolean hasCsat;

    public static TicketResponseDTO fromEntity(Ticket ticket, boolean hasCsat) {
        return TicketResponseDTO.builder()
                .id(ticket.getId())
                .title(ticket.getTitle())
                .description(ticket.getDescription())
                .status(ticket.getStatus())
                .priority(ticket.getPriority())
                .productId(ticket.getProductId())
                .customerId(ticket.getCustomerId())
                .assigneeId(ticket.getAssigneeId())
                .slaDeadline(ticket.getSlaDeadline())
                .slaBreached(ticket.getSlaBreached())
                .createdAt(ticket.getCreatedAt())
                .resolvedAt(ticket.getResolvedAt())
                .closedAt(ticket.getClosedAt())
                .hasCsat(hasCsat)
                .build();
    }

    public static TicketResponseDTO fromEntity(Ticket ticket) {
        return fromEntity(ticket, false);
    }
}
