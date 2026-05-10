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
@Schema(description = "Unclaim (bırakma) isteği — işlem nedeni (zorunlu)")
public class UnclaimRequestDTO {

    @NotBlank(message = "{field.notblank}")
    @Schema(description = "Bırakma nedeni", example = "Müşteri yanıt bekliyor", requiredMode = Schema.RequiredMode.REQUIRED)
    private String note;
}
