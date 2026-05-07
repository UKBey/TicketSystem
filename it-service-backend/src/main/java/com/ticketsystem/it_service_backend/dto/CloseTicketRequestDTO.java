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
@Schema(description = "Close ticket isteği — kapatma nedeni (zorunlu)")
public class CloseTicketRequestDTO {

    @NotBlank(message = "Close note cannot be blank")
    @Schema(description = "Kapatma nedeni", example = "Çözüm uygulanıp doğrulandı", requiredMode = Schema.RequiredMode.REQUIRED)
    private String note;
}
