package com.ticketsystem.it_service_backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * DTO for the event payload delivered to the jBPM callback endpoint.
 */
@Data
@Schema(description = "jBPM workflow callback olay yükü — KIE Server'dan backend'e gelen dahili bildirim")
public class WorkflowCallbackDTO {

    @NotNull(message = "{field.required}")
    @Schema(description = "Olayın ilişkili olduğu biletin ID'si", example = "42", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long ticketId;

    @NotBlank(message = "{field.notblank}")
    @Schema(description = "İşlenecek olay türü", example = "SLA_BREACHED", allowableValues = {"SLA_BREACHED", "PROCESS_COMPLETED"}, requiredMode = Schema.RequiredMode.REQUIRED)
    private String eventType;

    @Schema(description = "jBPM süreç örneği kimliği", example = "1001")
    private Long processInstanceId;

    @Schema(description = "Olaya bağlı ek açıklama/veri", example = "SLA deadline was 2026-04-21T17:00:00Z")
    private String additionalData;
}
