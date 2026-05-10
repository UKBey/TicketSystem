package com.ticketsystem.it_service_backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Manuel bilet atama isteği — Agent Admin tarafından kullanılır")
public class AssignTicketRequestDTO {

    @NotBlank(message = "{field.notblank}")
    @Schema(description = "Atanacak agent'ın Keycloak ID'si",
            example = "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String targetAgentId;

    @Schema(description = "Atama nedeni (opsiyonel)", example = "Uzman desteği gerekiyor")
    private String note;
}
