package com.ticketsystem.it_service_backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * jBPM'den (RestWorkItemHandler üzerinden) gelecek JSON payload modeli.
 */
@Data
public class WorkflowCallbackDTO {

    @NotNull(message = "ticketId zorunludur")
    private Long ticketId;

    @NotBlank(message = "eventType zorunludur")
    private String eventType;       // Örn: "SLA_BREACHED", "STATUS_UPDATED", "PROCESS_COMPLETED"

    private Long processInstanceId;

    private String additionalData;  // Ek mesaj veya veriler (Örn: "Priority: HIGH")
}
