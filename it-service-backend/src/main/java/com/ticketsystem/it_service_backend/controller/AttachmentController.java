package com.ticketsystem.it_service_backend.controller;

import com.ticketsystem.it_service_backend.entity.Attachment;
import com.ticketsystem.it_service_backend.service.AttachmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.multipart.MultipartFile;
import com.ticketsystem.it_service_backend.dto.AttachmentDTO;
import com.ticketsystem.it_service_backend.util.JwtUtils;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.log4j.Log4j2;

@Log4j2
@Tag(name = "Dosya Yönetimi", description = "Biletlere eklenen dosyaların (Attachment) yüklenmesi ve indirilmesi")
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class AttachmentController {

    private final AttachmentService attachmentService;

    // Bilete dosya ekler; boyut, tip ve yetki kontrolleri servis katmaninda calisir.
    @Operation(summary = "Dosya yükle", description = "Belirli bir bilete yeni bir dosya eki ekler.")
    @PostMapping("/tickets/{ticketId}/attachments")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'AGENT', 'MANAGER')")
    public ResponseEntity<AttachmentDTO> uploadAttachment(
            @PathVariable Long ticketId,
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal Jwt jwt) throws IOException {
        
        String uploaderId = jwt.getSubject();
        List<String> roles = JwtUtils.extractRoles(jwt);

        log.info("Bilet ID: {} için dosya yükleme isteği. Dosya: {}, Boyut: {} byte, Yükleyen: {}", 
                ticketId, file.getOriginalFilename(), file.getSize(), uploaderId);
        log.debug("Yükleyicinin rolleri: {}", roles);

        Attachment attachment = attachmentService.uploadAttachment(ticketId, uploaderId, roles, file);
        
        log.info("Dosya başarıyla yüklendi. Bilet ID: {}, Dosya ID: {}", ticketId, attachment.getId());

        return ResponseEntity.ok(AttachmentDTO.fromEntity(attachment));
    }

    // Bilete bagli tum dosya metaverilerini listeler.
    @Operation(summary = "Biletin dosyalarını listele", description = "Bilet ID'sine göre yüklenmiş tüm dosyaların listesini getirir.")
    @GetMapping("/tickets/{ticketId}/attachments")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'AGENT', 'MANAGER')")
    public ResponseEntity<List<AttachmentDTO>> getAttachments(@PathVariable Long ticketId) {
        log.info("Bilet ID: {} için ekli dosyaları listeleme isteği.", ticketId);
        
        List<Attachment> attachments = attachmentService.getTicketAttachments(ticketId);
        
        log.info("Bilet ID: {} için {} dosya listelendi.", ticketId, attachments.size());

        return ResponseEntity.ok(attachments.stream()
                .map(AttachmentDTO::fromEntity)
                .collect(Collectors.toList()));
    }

    // Dosya icerigini MIME tipi ve dosya adi bilgisiyle birlikte indirir.
    @Operation(summary = "Dosya indir", description = "Dosya ID'si ile dosya içeriğini indirir.")
    @GetMapping("/attachments/{id}")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'AGENT', 'MANAGER')")
    public ResponseEntity<byte[]> downloadAttachment(@PathVariable Long id) {
        log.info("Dosya ID: {} için indirme isteği.", id);

        Attachment attachment = attachmentService.getAttachment(id);
        
        log.info("Dosya başarıyla çekildi: {}, Tip: {}", attachment.getFileName(), attachment.getFileType());

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(attachment.getFileType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + attachment.getFileName() + "\"")
                .body(attachment.getContent());
    }

    // Dosyayi rol/sahiplik kurallarina gore siler.
    @Operation(summary = "Dosya sil", description = "Sisteme yüklenmiş bir dosyayı siler.")
    @DeleteMapping("/attachments/{id}")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'AGENT', 'MANAGER')")
    public ResponseEntity<Void> deleteAttachment(
            @PathVariable Long id,
            @AuthenticationPrincipal Jwt jwt) {
        
        String userId = jwt.getSubject();
        List<String> roles = JwtUtils.extractRoles(jwt);

        log.info("Dosya ID: {} için silme isteği. Siler: {}", id, userId);
        log.debug("Kullanıcının rolleri: {}", roles);

        attachmentService.deleteAttachment(id, userId, roles);

        log.info("Dosya başarıyla silindi. Dosya ID: {}", id);

        return ResponseEntity.noContent().build();
    }
}
