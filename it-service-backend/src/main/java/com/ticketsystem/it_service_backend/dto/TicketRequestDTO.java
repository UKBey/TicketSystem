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
 * Bilet oluşturma isteği; başlık, açıklama, öncelik ve ürün/topic referansını taşır.
 * Müşterinin bilet açma endpoint'i tarafından kullanılır; açıklama aynı zamanda ilk yorum olarak kaydedilir.
 */
@Schema(description = "Yeni destek bileti oluşturma isteğinin veri modeli")
public class TicketRequestDTO {

    @Schema(description = "Biletin kısa başlığı", example = "VPN bağlantısı kurulamıyor", requiredMode = Schema.RequiredMode.REQUIRED, maxLength = 255)
    private String title;

    @Schema(description = "Sorunun detaylı açıklaması. İlk yorum (comment) olarak da kaydedilir.", example = "Sabahtan beri kurumsal VPN'e bağlanamıyorum. Hata kodu: ERR_TIMEOUT", requiredMode = Schema.RequiredMode.REQUIRED)
    private String description;

    @Schema(description = "Öncelik seviyesi", example = "HIGH", allowableValues = {"LOW", "MEDIUM", "HIGH", "CRITICAL"}, requiredMode = Schema.RequiredMode.REQUIRED)
    private String priority;

    @Schema(description = "Biletin ait olduğu ürün/kategori ID'si", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long productId;

    @Schema(description = "Seçilen talep konusunun ID'si (ürüne ait, aktif bir konu olmalı)", example = "12", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long topicId;
}
