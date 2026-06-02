package com.ticketsystem.it_service_backend.service;

import com.ticketsystem.it_service_backend.dto.AttachmentDTO;
import com.ticketsystem.it_service_backend.entity.Attachment;
import com.ticketsystem.it_service_backend.entity.Ticket;
import com.ticketsystem.it_service_backend.repository.AttachmentRepository;
import com.ticketsystem.it_service_backend.util.AuthRoles;
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
 * Manages upload, listing, download and deletion flows for ticket attachments.
 *
 * <p>File content is stored in the DB as {@code bytea}. Uploads are validated against
 * a size limit (10 MB), an extension whitelist, and — for text-based files —
 * ERROR/WARNING keyword presence plus sensitive-data pattern checks (tokens, secrets,
 * private key blocks). Successful operations are broadcast via
 * {@link SimpMessagingTemplate} to the ticket topic.
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
     * Adds a new attachment to the given ticket. The uploader must have mutation
     * access to the ticket; size and extension rules must hold; .txt/.log files
     * must contain ERROR/WARNING keywords and must not include sensitive-data
     * patterns (tokens, passwords, private keys).
     *
     * <p>After a successful save, an "attachment added" event is published on the
     * {@code /topic/tickets/{id}} channel.
     *
     * @param ticketId target ticket ID
     * @param uploaderId ID of the uploading user
     * @param roles role list used for authorization
     * @param file multipart file to upload
     * @return the persisted {@link Attachment}
     * @throws IOException when the file content cannot be read
     * @throws IllegalArgumentException when size/extension/content rules are violated
     * @throws ResponseStatusException 403 if the user lacks mutation access to the ticket
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
     * Returns the attachment metadata list for the ticket. Throws 403 when the
     * user lacks access — this prevents the IDOR risk of enumerating another
     * ticket's attachments via sequential IDs.
     */
    public List<Attachment> getTicketAttachments(Long ticketId, String userId, List<String> roles) {
        log.debug("Bilet ID: {} için ekli dosyalar çekiliyor. Kullanıcı: {}", ticketId, userId);
        ticketService.getTicketWithAuth(ticketId, userId, roles);
        return attachmentRepository.findByTicketId(ticketId);
    }

    /**
     * Fetches a single attachment. The user's access to the parent ticket is
     * verified first, which prevents IDOR-style downloads of another user's
     * attachment via ID enumeration.
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
     * Deletes the attachment. AGENT_ADMIN can delete any attachment; other roles
     * can only delete attachments they uploaded. Ticket access is verified up
     * front via {@link #getAttachment}.
     *
     * @param id attachment ID to delete
     * @param userId user performing the action
     * @param roles role list of the user
     * @throws IllegalArgumentException when the attachment is not found
     * @throws ResponseStatusException 403 on missing authorization/ownership
     */
    public void deleteAttachment(Long id, String userId, List<String> roles) {
        Attachment attachment = getAttachment(id, userId, roles);

        log.info("Dosya silme işlemi. ID: {}, Siler: {}, Roller: {}", id, userId, roles);

        // ADMIN (global) ve LEAD_AGENT (yetkili ürünleri içinde) dosya sahipligi
        // aranmadan silebilir; ürün kapsamı getAttachment içindeki getTicketWithAuth
        // ile zaten doğrulandı.
        if (AuthRoles.isAdmin(roles) || AuthRoles.isLeadAgent(roles)) {
            log.info("Yükseltilmiş yetkiyle dosya siliniyor. Dosya ID: {}", id);
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
