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
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.log4j.Log4j2;

@Log4j2
@Tag(name = "Dosya Yönetimi", description = "Biletlere dosya eki yükleme, listeleme, indirme ve silme işlemleri")
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class AttachmentController {

    private final AttachmentService attachmentService;

    // Bilete dosya ekler; boyut, tip ve yetki kontrolleri servis katmaninda calisir.
    @Operation(summary = "Dosya yükle",
            description = """
                    Belirtilen bilete dosya eki yükler. Dosya veritabanında `BYTEA` olarak saklanır.
                    
                    **Kısıtlamalar:**
                    - Maksimum dosya boyutu: 10 MB
                    - İzin verilen tipler: Tüm MIME tipleri (sunucu tarafında ek filtreleme yapılabilir)
                    - Müşteri yalnızca kendi biletine dosya ekleyebilir
                    - Agent yalnızca üzerine atanan bilete dosya ekleyebilir
                    
                    Dosya metadata'sı (`id`, `fileName`, `fileType`, `uploaderId`, `createdAt`) yanıt olarak döner.
                    Dosya içeriği bu endpoint'ten dönmez; indirmek için `GET /api/attachments/{id}` kullanılır.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Dosya başarıyla yüklendi",
                    content = @Content(schema = @Schema(implementation = AttachmentDTO.class))),
            @ApiResponse(responseCode = "403", description = "Bu bilete dosya ekleme yetkiniz yok"),
            @ApiResponse(responseCode = "413", description = "Dosya boyutu izin verilen limiti aşıyor")
    })
    @PostMapping("/tickets/{ticketId}/attachments")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'AGENT', 'MANAGER')")
    public ResponseEntity<AttachmentDTO> uploadAttachment(
            @Parameter(description = "Dosyanın ekleneceği biletin ID'si", example = "42", required = true)
            @PathVariable Long ticketId,
            @Parameter(description = "Yüklenecek dosya", required = true)
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
    @Operation(summary = "Biletin dosyalarını listele",
            description = "Belirtilen bilete yüklenmiş tüm dosyaların metadata listesini getirir. Dosya içerikleri bu endpoint'ten dönmez.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Dosya listesi başarıyla döndü"),
            @ApiResponse(responseCode = "401", description = "Geçersiz veya eksik JWT token")
    })
    @GetMapping("/tickets/{ticketId}/attachments")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'AGENT', 'MANAGER')")
    public ResponseEntity<List<AttachmentDTO>> getAttachments(
            @Parameter(description = "Dosyaları listelenecek biletin ID'si", example = "42", required = true)
            @PathVariable Long ticketId) {
        log.info("Bilet ID: {} için ekli dosyaları listeleme isteği.", ticketId);
        
        List<Attachment> attachments = attachmentService.getTicketAttachments(ticketId);
        
        log.info("Bilet ID: {} için {} dosya listelendi.", ticketId, attachments.size());

        return ResponseEntity.ok(attachments.stream()
                .map(AttachmentDTO::fromEntity)
                .collect(Collectors.toList()));
    }

    // Dosya icerigini MIME tipi ve dosya adi bilgisiyle birlikte indirir.
    @Operation(summary = "Dosya indir",
            description = "Belirtilen ID'ye sahip dosyanın içeriğini orijinal MIME tipi ve dosya adıyla birlikte indirir. "
                    + "Tarayıcıda `Content-Disposition: attachment` başlığı ile doğrudan indirme tetiklenir.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Dosya içeriği başarıyla döndü",
                    content = @Content(mediaType = "application/octet-stream")),
            @ApiResponse(responseCode = "404", description = "Dosya bulunamadı")
    })
    @GetMapping("/attachments/{id}")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'AGENT', 'MANAGER')")
    public ResponseEntity<byte[]> downloadAttachment(
            @Parameter(description = "İndirilecek dosyanın ID'si", example = "55", required = true)
            @PathVariable Long id) {
        log.info("Dosya ID: {} için indirme isteği.", id);

        Attachment attachment = attachmentService.getAttachment(id);
        
        log.info("Dosya başarıyla çekildi: {}, Tip: {}", attachment.getFileName(), attachment.getFileType());

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(attachment.getFileType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + attachment.getFileName() + "\"")
                .body(attachment.getContent());
    }

    // Dosyayi rol/sahiplik kurallarina gore siler.
    @Operation(summary = "Dosya sil",
            description = "Yüklenen dosyayı kalıcı olarak siler. Müşteri yalnızca kendi yüklediği dosyayı silebilir, "
                    + "Agent yalnızca üzerine atanan biletin dosyasını silebilir, Manager herhangi bir dosyayı silebilir.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Dosya başarıyla silindi"),
            @ApiResponse(responseCode = "403", description = "Bu dosyayı silme yetkiniz yok"),
            @ApiResponse(responseCode = "404", description = "Dosya bulunamadı")
    })
    @DeleteMapping("/attachments/{id}")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'AGENT', 'MANAGER')")
    public ResponseEntity<Void> deleteAttachment(
            @Parameter(description = "Silinecek dosyanın ID'si", example = "55", required = true)
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
