package com.ticketsystem.it_service_backend.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.ticketsystem.it_service_backend.entity.Priority;
import com.ticketsystem.it_service_backend.entity.TicketStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
/**
 * A single alert-ticket row shown on the manager alerts panel.
 * Appears in one of the three lists inside {@link AlertsBacklogDTO} (breached/upcoming/waiting);
 * the {@code deadline}/{@code hoursUntilDeadline}/{@code hoursWaiting} fields are populated based on which list it belongs to.
 */
@Schema(description = "Bir alert ticket'ının özet bilgileri")
public class AlertTicketItemDTO {

    @Schema(description = "Ticket ID", example = "451")
    private Long ticketId;

    @Schema(description = "Ticket başlığı", example = "Mail sunucusu yanıt vermiyor")
    private String title;

    @Schema(description = "Öncelik seviyesi", example = "CRITICAL")
    private Priority priority;

    @Schema(description = "Müşteri kimliği", example = "usr-abc123")
    private String customerId;

    @Schema(description = "Müşteri adı soyadı", example = "Ahmet Yılmaz")
    private String customerName;

    @Schema(description = "SLA son tarihi (breachedSLA ve upcomingBreach için)")
    private ZonedDateTime deadline;

    @Schema(description = "Deadline'a kalan saat (negatif = geçilmiş, pozitif = yaklaşan)")
    private Double hoursUntilDeadline;

    @Schema(description = "Mevcut bekleme durumunda (giriş anından itibaren) geçen saat (waitingTooLong için)")
    private Double hoursWaiting;

    @Schema(description = "Ticket durumu — waitingTooLong satırlarında WAITING_FOR_CUSTOMER / RESOLVED ayrımı için", example = "WAITING_FOR_CUSTOMER")
    private TicketStatus status;
}
