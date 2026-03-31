package com.ticketsystem.it_service_backend.service;

import com.ticketsystem.it_service_backend.entity.Attachment;
import com.ticketsystem.it_service_backend.entity.Ticket;
import com.ticketsystem.it_service_backend.repository.AttachmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AttachmentService {

    private final AttachmentRepository attachmentRepository;
    private final TicketService ticketService;

    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024; // 5MB
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("pdf", "png", "jpg", "jpeg", "txt", "log");

    public Attachment uploadAttachment(Long ticketId, String uploaderId, List<String> roles, MultipartFile file) throws IOException {
        String fileName = StringUtils.cleanPath(file.getOriginalFilename());
        String extension = getFileExtension(fileName);

        // 1. Sıkı Yetki Kontrolü (Agent=Assignee, Customer=Owner)
        Ticket ticket = ticketService.validateMutationAccess(ticketId, uploaderId, roles);

        // 2. Boyut Kotrolü
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("Dosya boyutu 5MB sınırını aşamaz.");
        }

        // 3. Tip Kontrolü
        if (!ALLOWED_EXTENSIONS.contains(extension.toLowerCase())) {
            throw new IllegalArgumentException("Desteklenmeyen dosya tipi: " + extension);
        }

        byte[] content = file.getBytes();

        // 4. İçerik Kontrolü (.txt için)
        if ("txt".equalsIgnoreCase(extension)) {
            String textContent = new String(content, StandardCharsets.UTF_8);
            if (!textContent.contains("ERROR") && !textContent.contains("WARNING")) {
                throw new IllegalArgumentException(".txt dosyaları 'ERROR' veya 'WARNING' anahtar kelimelerini içermelidir.");
            }
        }

        Attachment attachment = Attachment.builder()
                .ticket(ticket)
                .uploaderId(uploaderId)
                .fileName(fileName)
                .fileType(file.getContentType())
                .content(content)
                .build();

        return attachmentRepository.save(attachment);
    }

    public List<Attachment> getTicketAttachments(Long ticketId) {
        return attachmentRepository.findByTicketId(ticketId);
    }

    public Attachment getAttachment(Long id) {
        return attachmentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Dosya bulunamadı: " + id));
    }

    public void deleteAttachment(Long id, String userId, List<String> roles) {
        Attachment attachment = getAttachment(id);
        
        // 1. MANAGER her şeyi silebilir
        if (roles.contains("MANAGER")) {
            attachmentRepository.delete(attachment);
            return;
        }

        // 2. Sadece yükleyen kişi silebilir
        if (!userId.equals(attachment.getUploaderId())) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN, "Sadece kendi yüklediğiniz dosyaları silebilirsiniz.");
        }

        attachmentRepository.delete(attachment);
    }

    private String getFileExtension(String fileName) {
        if (fileName == null || !fileName.contains(".")) {
            return "";
        }
        return fileName.substring(fileName.lastIndexOf(".") + 1);
    }
}
