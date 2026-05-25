package com.ticketsystem.it_service_backend.dto;

import com.ticketsystem.it_service_backend.entity.Attachment;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.ZonedDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
/**
 * Metadata response for a file attachment on a ticket — file name, MIME type and uploader info.
 * The file content is not included in this DTO; it is fetched via a separate download endpoint.
 */
@Schema(description = "Dosya eki metadata bilgisi — dosya içeriği bu DTO'da yer almaz, ayrı endpoint üzerinden indirilir")
public class AttachmentDTO {

    @Schema(description = "Dosya ekinin benzersiz kimliği", example = "55")
    private Long id;

    @Schema(description = "Orijinal dosya adı", example = "hata_ekran_goruntusu.png")
    private String fileName;

    @Schema(description = "Dosyanın MIME tipi", example = "image/png")
    private String fileType;

    @Schema(description = "Dosyayı yükleyen kullanıcının Keycloak ID'si", example = "a1b2c3d4-e5f6-7890-abcd-ef1234567890")
    private String uploaderId;

    @Schema(description = "Yükleme tarihi", example = "2026-04-21T12:00:00+03:00")
    private ZonedDateTime createdAt;

    public static AttachmentDTO fromEntity(Attachment attachment) {
        return AttachmentDTO.builder()
                .id(attachment.getId())
                .fileName(attachment.getFileName())
                .fileType(attachment.getFileType())
                .uploaderId(attachment.getUploaderId())
                .createdAt(attachment.getCreatedAt())
                .build();
    }
}
