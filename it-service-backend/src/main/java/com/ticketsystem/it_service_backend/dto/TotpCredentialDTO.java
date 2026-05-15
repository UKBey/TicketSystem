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
@Schema(description = "Kullanıcının kayıtlı TOTP (authenticator) cihazı özet bilgisi")
public class TotpCredentialDTO {

    @Schema(description = "Keycloak credential ID (silme isteğinde kullanılır)",
            example = "6c230f2c-0b3d-409d-9bbf-28d423883549")
    private String id;

    @Schema(description = "Kullanıcının cihaza verdiği etiket", example = "iPhone")
    private String userLabel;

    @Schema(description = "Cihazın kaydedildiği epoch-millis zamanı", example = "1715792400000")
    private Long createdDate;
}
