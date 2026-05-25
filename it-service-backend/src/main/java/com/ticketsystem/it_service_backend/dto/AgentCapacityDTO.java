package com.ticketsystem.it_service_backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
/**
 * Manuel atama UI'ında bir ajanın mevcut yük ve kapasite bilgisini gösteren özet.
 * {@code isFull=true} olduğunda atama seçeneği devre dışı bırakılır.
 */
@Schema(description = "Agent kapasite bilgisi — atama UI'ı için")
public class AgentCapacityDTO {
    
    @Schema(description = "Agent Keycloak ID", example = "a1b2c3d4-e5f6-7890-abcd-ef1234567890")
    private String agentId;
    
    @Schema(description = "Agent tam adı", example = "Ukbe Taha")
    private String agentName;
    
    @Schema(description = "Mevcut aktif bilet sayısı", example = "3")
    private Long currentActiveTickets;
    
    @Schema(description = "Maksimum limit (null = limitsiz)", example = "5")
    private Integer maxLimit;
    
    @Schema(description = "Limit doldu mu?", example = "false")
    private Boolean isFull;
}
