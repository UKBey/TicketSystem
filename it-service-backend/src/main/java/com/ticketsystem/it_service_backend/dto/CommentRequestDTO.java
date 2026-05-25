package com.ticketsystem.it_service_backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.*;

/**
 * Request body for adding a comment to a ticket. Previously accepted as a raw
 * {@code Map<String,String>}; this class provides a Swagger schema and
 * compile-time type safety.
 *
 * <p>Business rules (message length, valid comment types) are also enforced
 * in the service layer — Bean Validation here is defense-in-depth at the DTO level.
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
