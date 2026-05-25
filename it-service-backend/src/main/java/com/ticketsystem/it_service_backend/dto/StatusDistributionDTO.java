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
 * Distribution of tickets by status (NEW, IN_PROGRESS, etc.).
 * Count response consumed by the dashboard's pie/bar chart component.
 */
@Schema(description = "Bilet durum dağılımı — dashboard chart verisi")
public class StatusDistributionDTO {

    @Schema(description = "NEW durumundaki bilet sayısı", example = "44")
    private Long newCount;

    @Schema(description = "IN_PROGRESS durumundaki bilet sayısı", example = "103")
    private Long inProgressCount;

    @Schema(description = "WAITING_FOR_CUSTOMER durumundaki bilet sayısı", example = "54")
    private Long waitingForCustomerCount;

    @Schema(description = "RESOLVED durumundaki bilet sayısı", example = "38")
    private Long resolvedCount;

    @Schema(description = "CLOSED durumundaki bilet sayısı", example = "6")
    private Long closedCount;

    @Schema(description = "Toplam bilet sayısı", example = "245")
    private Long totalCount;
}