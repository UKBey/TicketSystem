package com.ticketsystem.it_service_backend.service;

import com.ticketsystem.it_service_backend.dto.AttachmentDTO;
import com.ticketsystem.it_service_backend.entity.Attachment;
import com.ticketsystem.it_service_backend.entity.Ticket;
import com.ticketsystem.it_service_backend.repository.AttachmentRepository;
import com.ticketsystem.it_service_backend.websocket.TicketWebSocketEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import lombok.extern.log4j.Log4j2;

/**
 * Bilet eklerinin (attachment) yükleme, listeleme, indirme ve silme akışlarını yönetir.
 *
 * <p>Dosya içeriği DB'de {@code bytea} olarak saklanır. Yükleme aşamasında boyut
 * (10 MB), uzantı whitelist'i ve metin tabanlı dosyalarda ERROR/WARNING anahtar
 * kelime + hassas veri (token, secret, private key blokları) kontrolleri uygulanır.
 * Başarılı işlemler {@link SimpMessagingTemplate} üzerinden ilgili ticket topic'ine
 * yayınlanır.
 */
@Log4j2
@Service
@RequiredArgsConstructor
public class AttachmentService {

    private final AttachmentRepository attachmentRepository;
    private final TicketService ticketService;
    private final SimpMessagingTemplate messagingTemplate;

        private static final long MAX_FILE_SIZE = 10L * 1024 * 1024; // Yukleme ust limiti.
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("pdf", "png", "jpg", "jpeg", "txt", "log");
        private static final Pattern SENSITIVE_KEYWORD_PATTERN = Pattern.compile(
            "(?i)\\b(password|passwd|pwd|secret|token|api[_-]?key|access[_-]?key|private[_-]?key)\\b");
        private static final Pattern BEARER_TOKEN_PATTERN = Pattern.compile("(?i)bearer\\s+[A-Za-z0-9._\\-+/=]{10,}");
        private static final Pattern PRIVATE_KEY_BLOCK_PATTERN = Pattern.compile(
            "(?s)-----BEGIN [A-Z ]+ PRIVATE KEY-----.*?-----END [A-Z ]+ PRIVATE KEY-----");

    /**
     * Verilen bilete yeni bir dosya ekler. Yükleyenin bilet üzerinde mutation
     * yetkisi olmalı; boyut ve uzantı kuralları sağlanmalı; .txt/.log dosyaları
     * için ERROR/WARNING anahtar kelimeleri zorunludur ve hassas veri kalıbı
     * (token, password, private key) içermemelidir.
     *
     * <p>Başarılı kayıttan sonra {@code /topic/tickets/{id}} kanalına attachment
     * eklendi olayı yayınlanır.
     *
     * @param ticketId hedef bilet ID
     * @param uploaderId yükleyen kullanıcının ID'si
     * @param roles yetki doğrulamasında kullanılacak rol listesi
     * @param file yüklenecek multipart dosya
     * @return kaydedilmiş {@link Attachment}
     * @throws IOException dosya içeriği okunamadığında
     * @throws IllegalArgumentException boyut/uzantı/içerik kuralları ihlal edildiğinde
     * @throws ResponseStatusException 403 — bilete mutation yetkisi yoksa
     */
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

        messagingTemplate.convertAndSend(
                "/topic/tickets/" + ticketId,
                TicketWebSocketEvent.attachmentAdded(AttachmentDTO.fromEntity(savedAttachment)));

        return savedAttachment;
    }

    /**
     * Bilete ait dosya metadata listesini döner. Kullanıcının bilete erişim
     * yetkisi yoksa 403 fırlatır — IDOR riskini (başka biletin eklerini
     * sıralı ID ile enumerate etmek) engeller.
     */
    public List<Attachment> getTicketAttachments(Long ticketId, String userId, List<String> roles) {
        log.debug("Bilet ID: {} için ekli dosyalar çekiliyor. Kullanıcı: {}", ticketId, userId);
        ticketService.getTicketWithAuth(ticketId, userId, roles);
        return attachmentRepository.findByTicketId(ticketId);
    }

    /**
     * Tek bir dosyayı çeker. Önce dosyanın bağlı olduğu bilete kullanıcının
     * erişim hakkı doğrulanır → başka kullanıcının dosyasını ID enumerate
     * ederek indirme (IDOR) engellenir.
     */
    public Attachment getAttachment(Long id, String userId, List<String> roles) {
        Attachment attachment = attachmentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("error.attachment.not.found"));
        Ticket ticket = attachment.getTicket();
        if (ticket == null) {
            log.warn("Dosya ID: {} bağlı olduğu bilete sahip değil — erişim reddedildi.", id);
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "error.attachment.access.forbidden");
        }
        ticketService.getTicketWithAuth(ticket.getId(), userId, roles);
        return attachment;
    }

    /**
     * Eki siler. AGENT_ADMIN her dosyayı silebilir; diğer rollerde yalnızca
     * yükleyen kullanıcı silebilir. Bilet erişimi {@link #getAttachment} ile
     * önceden doğrulanır.
     *
     * @param id silinecek attachment ID
     * @param userId işlemi yapan kullanıcı
     * @param roles kullanıcının rolleri
     * @throws IllegalArgumentException attachment bulunamazsa
     * @throws ResponseStatusException 403 — yetki/sahiplik yoksa
     */
    public void deleteAttachment(Long id, String userId, List<String> roles) {
        Attachment attachment = getAttachment(id, userId, roles);

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
