package com.ticketsystem.it_service_backend.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
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
@Schema(description = "Bir alert ticket'ının özet bilgileri")
public class AlertTicketItemDTO {

    @Schema(description = "Ticket ID", example = "451")
    private Long ticketId;

    @Schema(description = "Ticket başlığı", example = "Mail sunucusu yanıt vermiyor")
    private String title;

    @Schema(description = "Öncelik seviyesi", example = "CRITICAL")
    private String priority;

    @Schema(description = "Müşteri kimliği", example = "usr-abc123")
    private String customerId;

    @Schema(description = "SLA son tarihi (breachedSLA ve upcomingBreach için)")
    private ZonedDateTime deadline;

    @Schema(description = "Deadline'a kalan saat (negatif = geçilmiş, pozitif = yaklaşan)")
    private Double hoursUntilDeadline;

    @Schema(description = "WAITING_FOR_CUSTOMER durumunda geçen saat (waitingTooLong için)")
    private Double hoursWaiting;
}
