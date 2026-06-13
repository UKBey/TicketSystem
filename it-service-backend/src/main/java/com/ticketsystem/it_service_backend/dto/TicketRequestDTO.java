package com.ticketsystem.it_service_backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
/**
 * Ticket creation request; carries title, description, priority and product/topic references.
 * Used by the customer-facing ticket creation endpoint; the description is also persisted as the first comment.
 */
@Schema(description = "Yeni destek bileti oluşturma isteğinin veri modeli")
public class TicketRequestDTO {

    @Schema(description = "Biletin kısa başlığı", example = "VPN bağlantısı kurulamıyor", requiredMode = Schema.RequiredMode.REQUIRED, maxLength = 100)
    @NotBlank(message = "{field.notblank}")
    @Size(max = 100, message = "{validation.ticket.title.size}")
    private String title;

    @Schema(description = "Sorunun detaylı açıklaması. İlk yorum (comment) olarak da kaydedilir.", example = "Sabahtan beri kurumsal VPN'e bağlanamıyorum. Hata kodu: ERR_TIMEOUT", requiredMode = Schema.RequiredMode.REQUIRED, maxLength = 500)
    @NotBlank(message = "{field.notblank}")
    @Size(max = 500, message = "{validation.ticket.description.size}")
    private String description;

    @Schema(description = "Öncelik seviyesi", example = "HIGH", allowableValues = {"LOW", "MEDIUM", "HIGH", "CRITICAL"}, requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "{field.notblank}")
    @Pattern(regexp = "LOW|MEDIUM|HIGH|CRITICAL", message = "{validation.ticket.priority.invalid}")
    private String priority;

    @Schema(description = "Biletin ait olduğu ürün/kategori ID'si", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "{field.required}")
    private Long productId;

    @Schema(description = "Seçilen talep konusunun ID'si (ürüne ait, aktif bir konu olmalı). Ürünün hiç aktif konusu yoksa boş bırakılabilir (konusuz bilet).", example = "12", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private Long topicId;
}
