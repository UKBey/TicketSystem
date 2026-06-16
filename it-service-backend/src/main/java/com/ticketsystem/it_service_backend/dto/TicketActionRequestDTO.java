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
 * Request body for ticket lifecycle action endpoints (wait / resume / resolve / reopen).
 *
 * <p>Both fields are optional at the validation layer; the service enforces a reason
 * code where the action requires one (e.g. {@code resolve}). The action itself — not
 * a free-form target status — determines the transition, so there is no {@code status}
 * field: the source state is guarded server-side.
 */
@Schema(description = "Bilet yaşam döngüsü eylem isteği (beklet/devam/çöz/yeniden aç). Hedef statü yoktur; eylem geçişi belirler.")
public class TicketActionRequestDTO {

    @Schema(description = "Sebep kodu (resolve eyleminde zorunlu)", example = "SOLUTION_PROVIDED")
    private String reasonCode;

    @Schema(description = "Serbest metin açıklama. reasonCode=OTHER ise zorunlu.", example = "Çözüm e-posta ile iletildi.")
    private String note;
}
