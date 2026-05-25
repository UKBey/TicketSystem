package com.ticketsystem.it_service_backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

/**
 * Lightweight model representing an agent who has claimed a ticket.
 * Used as elements of the {@link TicketResponseDTO#getClaimers()} list.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClaimerDTO {
    private String agentId;
    private String agentName;
    private LocalDateTime claimedAt;
}
