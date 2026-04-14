package com.ticketsystem.it_service_backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * jBPM callback endpoint'ine gelen olay yukunun DTO karsiligidir.
 */
@Data
public class WorkflowCallbackDTO {

    @NotNull(message = "ticketId zorunludur")
    private Long ticketId;

    @NotBlank(message = "eventType zorunludur")
    private String eventType;       // Islenecek olay turunu belirtir.

    private Long processInstanceId;

    private String additionalData;  // Olaya bagli ek aciklama/veri alani.
}
