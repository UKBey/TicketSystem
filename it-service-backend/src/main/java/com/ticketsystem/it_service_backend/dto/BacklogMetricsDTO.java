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
 * Atanmamış ve NEW durumundaki biletler için backlog özet metrikleri.
 * {@link AlertsBacklogDTO} içinde dashboard alerts paneline servis edilir.
 */
@Schema(description = "Bilet birikimi ve atanmamış ticket metrikleri")
public class BacklogMetricsDTO {

    @Schema(description = "Atanmamış açık ticket sayısı", example = "28")
    private long unassignedCount;

    @Schema(description = "NEW statüsünde bekleyen ticket sayısı", example = "15")
    private long newTicketsWaiting;

    @Schema(description = "Açık biletlerin ortalama bekleme süresi (saat)", example = "4.2")
    private double avgWaitingHours;
}
