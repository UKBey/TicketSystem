package com.ticketsystem.it_service_backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;

/**
 * Lightweight ticket row for the personal dashboards' "recent tickets" lists.
 * Carries just enough to render a clickable summary line (id, title, status, priority, date).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Kişisel dashboard'lardaki 'son biletler' listesi için hafif bilet satırı")
public class RecentTicketDTO {

    @Schema(description = "Bilet ID", example = "1024")
    private Long id;

    @Schema(description = "Bilet başlığı", example = "VPN bağlantısı kopuyor")
    private String title;

    @Schema(description = "Bilet durumu", example = "IN_PROGRESS")
    private String status;

    @Schema(description = "Öncelik", example = "HIGH")
    private String priority;

    @Schema(description = "Oluşturulma zamanı")
    private ZonedDateTime createdAt;
}
