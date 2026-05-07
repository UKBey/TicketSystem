package com.ticketsystem.it_service_backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClaimerDTO {
    private String agentId;
    private String agentName;
    private LocalDateTime claimedAt;
}
