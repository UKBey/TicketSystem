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
import java.util.regex.Pattern;

import lombok.extern.log4j.Log4j2;

@Log4j2
@Service
@RequiredArgsConstructor
public class AttachmentService {

    private final AttachmentRepository attachmentRepository;
    private final TicketService ticketService;

        private static final long MAX_FILE_SIZE = 10 * 1024 * 1024; // Yukleme ust limiti.
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("pdf", "png", "jpg", "jpeg", "txt", "log");
        private static final Pattern SENSITIVE_KEYWORD_PATTERN = Pattern.compile(
            "(?i)\\b(password|passwd|pwd|secret|token|api[_-]?key|access[_-]?key|private[_-]?key)\\b");
        private static final Pattern BEARER_TOKEN_PATTERN = Pattern.compile("(?i)bearer\\s+[A-Za-z0-9._\\-+/=]{10,}");
        private static final Pattern PRIVATE_KEY_BLOCK_PATTERN = Pattern.compile(
            "(?s)-----BEGIN [A-Z ]+ PRIVATE KEY-----.*?-----END [A-Z ]+ PRIVATE KEY-----");

    public Attachment uploadAttachment(Long ticketId, String uploaderId, List<String> roles, MultipartFile file) throws IOException {
        String fileName = StringUtils.cleanPath(file.getOriginalFilename());
        String extension = getFileExtension(fileName);

        log.info("Dosya yükleme işlemi başlatıldı. Bilet ID: {}, Dosya: {}, Yükleyen: {}", ticketId, fileName, uploaderId);

        // Dosya ekleme, sadece kaydi degistirme yetkisi olan kullanicilara aciktir.
        Ticket ticket = ticketService.validateMutationAccess(ticketId, uploaderId, roles);

        // Buyuk dosyalari erken reddederek depolama ve performans maliyeti sinirlanir.
        if (file.getSize() > MAX_FILE_SIZE) {
            log.warn("Yükleme reddedildi: Dosya boyutu sınırda ({} bytes)", file.getSize());
            throw new IllegalArgumentException("error.attachment.size.exceeded");
        }

        // Yalnizca izinli uzantilara sahip dosyalar kabul edilir.
        if (!ALLOWED_EXTENSIONS.contains(extension.toLowerCase())) {
            log.warn("Yükleme reddedildi: Desteklenmeyen dosya uzantısı ({})", extension);
            throw new IllegalArgumentException("error.attachment.unsupported.type");
        }

        byte[] content = file.getBytes();

        // Metin tabanli dosyalarda beklenen anahtar ifadeler ve hassas veri kaliplari kontrol edilir.
        if (isTextBasedExtension(extension)) {
            log.debug("Metin tabanlı dosya içerik kontrolü yapılıyor...");
            String textContent = new String(content, StandardCharsets.UTF_8);
            if (!textContent.contains("ERROR") && !textContent.contains("WARNING")) {
                log.warn("Yükleme reddedildi: .txt dosyası gerekli anahtar kelimeleri içermiyor (ERROR/WARNING)");
                throw new IllegalArgumentException("error.attachment.txt.missing.keywords");
            }
            if (containsSensitiveInfo(textContent)) {
                log.warn("Yükleme reddedildi: hassas bilgi kalıbı tespit edildi (bilet: {}, dosya: {})", ticketId, fileName);
                throw new IllegalArgumentException("error.attachment.sensitive.data");
            }
        }

        Attachment attachment = Attachment.builder()
                .ticket(ticket)
                .uploaderId(uploaderId)
                .fileName(fileName)
                .fileType(file.getContentType())
                .content(content)
                .build();

        Attachment savedAttachment = attachmentRepository.save(attachment);
        log.info("Dosya başarıyla veritabanına kaydedildi. ID: {}", savedAttachment.getId());
        return savedAttachment;
    }

    public List<Attachment> getTicketAttachments(Long ticketId) {
        log.debug("Bilet ID: {} için ekli dosyalar çekiliyor.", ticketId);
        return attachmentRepository.findByTicketId(ticketId);
    }

    public Attachment getAttachment(Long id) {
        return attachmentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("error.attachment.not.found"));
    }

    public void deleteAttachment(Long id, String userId, List<String> roles) {
        Attachment attachment = getAttachment(id);
        
        log.info("Dosya silme işlemi. ID: {}, Siler: {}, Roller: {}", id, userId, roles);

        // Agent admin rolunde dosya sahipligi aranmadan silme izni vardir.
        if (roles.contains("AGENT_ADMIN")) {
            log.info("Agent admin yetkisiyle dosya siliniyor. Dosya ID: {}", id);
            attachmentRepository.delete(attachment);
            return;
        }

        // Yonetici disinda silme islemi sadece dosyayi yukleyen kullaniciya aciktir.
        if (!userId.equals(attachment.getUploaderId())) {
            log.warn("Silme reddedildi: Kullanıcı ({}) dosyanın sahibi değil (Sahibi: {})", userId, attachment.getUploaderId());
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN, "error.attachment.delete.own.only");
        }

        attachmentRepository.delete(attachment);
        log.info("Dosya başarıyla silindi. ID: {}", id);
    }

    private String getFileExtension(String fileName) {
        if (fileName == null || !fileName.contains(".")) {
            return "";
        }
        return fileName.substring(fileName.lastIndexOf(".") + 1);
    }

    private boolean isTextBasedExtension(String extension) {
        return "txt".equalsIgnoreCase(extension) || "log".equalsIgnoreCase(extension);
    }

    private boolean containsSensitiveInfo(String textContent) {
        return SENSITIVE_KEYWORD_PATTERN.matcher(textContent).find()
                || BEARER_TOKEN_PATTERN.matcher(textContent).find()
                || PRIVATE_KEY_BLOCK_PATTERN.matcher(textContent).find();
    }
}
