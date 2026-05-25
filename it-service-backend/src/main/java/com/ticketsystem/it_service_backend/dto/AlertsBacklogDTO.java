package com.ticketsystem.it_service_backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
/**
 * Response that feeds the manager dashboard's "alerts" panel.
 * Contains tickets that have breached or are about to breach SLA, those waiting too long on the customer, and a backlog summary.
 */
@Schema(description = "SLA breach uyarıları ve backlog metrikleri")
public class AlertsBacklogDTO {

    @Schema(description = "SLA'yı zaten aşmış açık biletler (en eski breach önce, max 10)")
    private List<AlertTicketItemDTO> breachedSLA;

    @Schema(description = "4 saat içinde SLA'yı aşacak açık biletler (deadline yakınlığına göre, max 10)")
    private List<AlertTicketItemDTO> upcomingBreach;

    @Schema(description = "3+ gün WAITING_FOR_CUSTOMER statüsünde olan biletler (en eski önce, max 10)")
    private List<AlertTicketItemDTO> waitingTooLong;

    @Schema(description = "Backlog özet metrikleri")
    private BacklogMetricsDTO backlogMetrics;
}
