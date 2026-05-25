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
 * Agent + ürün ikilisi için özel aktif-bilet limiti tanımlama isteği.
 * Agent admin sayfası tarafından kullanılır; {@code useCustomLimit=false} ise ürünün varsayılanı uygulanır.
 */
@Schema(description = "Agent için ürün bazlı özel limit ayarlama modeli")
public class AgentProductLimitRequestDTO {

    @Schema(description = "Özel limit kullanılacak mı?", example = "true")
    private boolean useCustomLimit;

    @Schema(description = "Özel maksimum aktif bilet limiti", example = "3", nullable = true)
    private Integer maxActiveTickets;
}