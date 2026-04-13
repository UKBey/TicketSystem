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
    private String productName;
    private String customerId;
    private String customerName;
    private String assigneeId;
    private String assigneeName;
    private ZonedDateTime slaDeadline;
    private Boolean slaBreached;
    private Long slaElapsedMs;
    private ZonedDateTime slaPausedAt;
    private ZonedDateTime createdAt;
    private ZonedDateTime resolvedAt;
    private ZonedDateTime closedAt;
    private Boolean hasCsat;
    private java.util.Map<String, Long> slaInfo;

    public static TicketResponseDTO fromEntity(Ticket ticket, boolean hasCsat, String productName, String customerName, String assigneeName) {
        return TicketResponseDTO.builder()
                .id(ticket.getId())
                .title(ticket.getTitle())
                .description(ticket.getDescription())
                .status(ticket.getStatus())
                .priority(ticket.getPriority())
                .productId(ticket.getProductId())
                .productName(productName)
                .customerId(ticket.getCustomerId())
                .customerName(customerName)
                .assigneeId(ticket.getAssigneeId())
                .assigneeName(assigneeName)
                .slaDeadline(ticket.getSlaDeadline())
                .slaBreached(ticket.getSlaBreached())
                .slaElapsedMs(ticket.getSlaElapsedMs())
                .slaPausedAt(ticket.getSlaPausedAt())
                .createdAt(ticket.getCreatedAt())
                .resolvedAt(ticket.getResolvedAt())
                .closedAt(ticket.getClosedAt())
                .hasCsat(hasCsat)
                .build();
    }

    public static TicketResponseDTO fromEntity(Ticket ticket, String productName, String customerName, String assigneeName) {
        return fromEntity(ticket, false, productName, customerName, assigneeName);
    }
}
