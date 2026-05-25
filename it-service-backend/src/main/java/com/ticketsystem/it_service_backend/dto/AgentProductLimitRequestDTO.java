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
 * Request for setting a custom active-ticket limit on an agent + product pair.
 * Used by the agent admin page; when {@code useCustomLimit=false} the product default applies instead.
 */
@Schema(description = "Agent için ürün bazlı özel limit ayarlama modeli")
public class AgentProductLimitRequestDTO {

    @Schema(description = "Özel limit kullanılacak mı?", example = "true")
    private boolean useCustomLimit;

    @Schema(description = "Özel maksimum aktif bilet limiti", example = "3", nullable = true)
    private Integer maxActiveTickets;
}