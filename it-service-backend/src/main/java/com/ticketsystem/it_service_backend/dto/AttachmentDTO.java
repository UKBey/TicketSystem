package com.ticketsystem.it_service_backend.dto;

import com.ticketsystem.it_service_backend.entity.Attachment;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.ZonedDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AttachmentDTO {
    private Long id;
    private String fileName;
    private String fileType;
    private String uploaderId;
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
