package com.ticketsystem.it_service_backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.*;

/**
 * Bilete yorum ekleme isteği gövdesi. Önceden {@code Map<String,String>} olarak
 * alınıyordu; bu sınıf Swagger schema'sını ve compile-time tür güvenliğini sağlar.
 *
 * <p>Iş kuralı (mesaj uzunluğu, geçerli yorum tipleri) servis katmanında da
 * doğrulanır — burada DTO seviyesinde Bean Validation defense-in-depth.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Bilete yorum ekleme isteği")
public class CommentRequestDTO {

    @Schema(description = "Yorum metni. Boş olamaz; en fazla 500 karakter.",
            example = "VPN ayarlarınızı kontrol ettim, tekrar deneyin.",
            maxLength = 500,
            requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank
    @Size(max = 500)
    private String message;

    @Schema(description = "Yorum tipi: müşteriye görünür yanıt (EXTERNAL) veya yalnız ajanların gördüğü dahili not (INTERNAL).",
            example = "EXTERNAL",
            allowableValues = {"EXTERNAL", "INTERNAL"},
            requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank
    @Pattern(regexp = "EXTERNAL|INTERNAL", message = "type must be EXTERNAL or INTERNAL")
    private String type;
}
