package com.ticketsystem.it_service_backend.service;

import com.ticketsystem.it_service_backend.dto.CommentDTO;
import com.ticketsystem.it_service_backend.entity.Comment;
import com.ticketsystem.it_service_backend.entity.Ticket;
import com.ticketsystem.it_service_backend.entity.User;
import com.ticketsystem.it_service_backend.repository.CommentRepository;
import com.ticketsystem.it_service_backend.repository.UserRepository;
import com.ticketsystem.it_service_backend.websocket.TicketWebSocketEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import lombok.extern.log4j.Log4j2;

/**
 * Bilet yorum (comment) ekleme ve listeleme akışı.
 *
 * <p>EXTERNAL yorumlar her iki tarafça da görülebilir; INTERNAL yorumlar yalnızca
 * ajanlara açıktır. Kullanıcı başına basit bir in-memory cooldown ve maks. uzunluk
 * doğrulaması uygulanır (config: {@code app.comments.*}). Kayıttan sonra
 * {@link NotificationService} bildirim üretir ve STOMP üzerinden ilgili topic'e
 * yayın yapılır. Müşteri WAITING_FOR_CUSTOMER bir bilete yorum yazarsa statü
 * otomatik IN_PROGRESS'e çekilir.
 */
@Log4j2
@Service
@RequiredArgsConstructor
public class CommentService {

    private final CommentRepository commentRepository;
    private final TicketService ticketService;
    private final NotificationService notificationService;
    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;

    private final ConcurrentHashMap<String, Instant> lastCommentTime = new ConcurrentHashMap<>();

    // B-10 — cooldown ve max length artik application.yml/env ile yapilandirilabilir.
    // DB-level constraint (V23 char_length CHECK) yine 500 olarak sabit; bu degerden
    // YUKSEK bir maxLength yapilandirirsaniz DB tarafinda fail eder. Asagi cekmek
    // (orn. 200) guvenli — service katmani daha agresif reddeder.
    @Value("${app.comments.cooldown-seconds:5}")
    private long cooldownSeconds;

    @Value("${app.comments.max-length:500}")
    private int maxMessageLength;

    /**
     * Bilete yeni bir yorum ekler.
     *
     * <p>Doğrulamalar: uzunluk (config'e bağlı), per-user cooldown, mutasyon yetkisi
     * ve INTERNAL yorum sadece ajan rolleriyle. Başarılı kayıttan sonra bildirim
     * ve WebSocket olayı tetiklenir; müşteri WAITING_FOR_CUSTOMER bilete yazdıysa
     * statü IN_PROGRESS'e çekilir.
     *
     * @param ticketId hedef bilet ID
     * @param message yorum metni
     * @param type yorum tipi (EXTERNAL veya INTERNAL); null/boşsa EXTERNAL
     * @param userId yorumu yazan kullanıcı
     * @param roles kullanıcının rolleri
     * @return kaydedilmiş {@link Comment}
     * @throws ResponseStatusException 400 uzunluk, 429 cooldown, 403 yetki ihlali
     */
    @Transactional
    public Comment addComment(Long ticketId, String message, String type, String userId, List<String> roles) {
        log.info("Yorum ekleme işlemi. Bilet ID: {}, Kullanıcı: {}, Tip: {}", ticketId, userId, type);

        if (message != null && message.length() > maxMessageLength) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "error.comment.message.too.long");
        }

        Instant last = lastCommentTime.get(userId);
        if (last != null && Instant.now().isBefore(last.plusSeconds(cooldownSeconds))) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(429), "error.comment.rate.limit");
        }

        // Yorum ekleme, mutasyon yetkisi denetiminden gecmeden ilerlemez.
        Ticket ticket = ticketService.validateMutationAccess(ticketId, userId, roles);

        // Sadece CUSTOMER olan kullanici dahili not birakamaz.
        boolean isOnlyCustomer = roles.contains("CUSTOMER") && !roles.contains("AGENT") && !roles.contains("AGENT_ADMIN");
        if (isOnlyCustomer && "INTERNAL".equals(type)) {
            log.warn("Yorum reddedildi: Müşteri (ID: {}) dahili yorum eklemeye çalıştı.", userId);
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "error.comment.customer.type.forbidden");
        }

        Comment comment = Comment.builder()
                .ticket(ticket)
                .message(message)
                .type(type != null ? type : "EXTERNAL")
                .authorId(userId)
                .build();

        Comment savedComment = commentRepository.save(comment);
        lastCommentTime.put(userId, Instant.now());
        log.info("Yorum başarıyla kaydedildi. Bilet ID: {}, Yorum ID: {}", ticketId, savedComment.getId());

        notificationService.notifyCommentAdded(ticket, savedComment);

        broadcastComment(ticketId, savedComment);

        // Musteri yaniti bekleme durumunu bozdugunda bilet tekrar calisma durumuna cekilir.
        if ("WAITING_FOR_CUSTOMER".equals(ticket.getStatus()) && ticket.getCustomerId().equals(userId)) {
            log.info("Müşteri yanıtı algılandı. Bilet statüsü WAITING_FOR_CUSTOMER'dan IN_PROGRESS'e çekiliyor.");
            ticketService.updateTicketStatus(ticketId, "IN_PROGRESS", null, null, userId, roles);
        }

        return savedComment;
    }

    // INTERNAL yorumlar agent-only topic'e gider; EXTERNAL'lar genel ticket topic'ine.
    private void broadcastComment(Long ticketId, Comment comment) {
        User author = comment.getAuthorId() != null
                ? userRepository.findById(comment.getAuthorId()).orElse(null)
                : null;
        String authorName = author != null ? author.getFullName() : "Unknown";
        String authorRole = author != null ? author.getRole() : null;
        CommentDTO dto = CommentDTO.fromEntity(comment, authorName, authorRole);
        String destination = "INTERNAL".equals(comment.getType())
                ? "/topic/tickets/" + ticketId + "/internal"
                : "/topic/tickets/" + ticketId;
        messagingTemplate.convertAndSend(destination, TicketWebSocketEvent.commentAdded(dto));
    }

    /**
     * Verilen biletin yorumlarını eskiye-yeniye sıralı döner. Müşteri rolünde
     * INTERNAL yorumlar filtrelenir. Bilet erişim yetkisi {@link TicketService} ile
     * doğrulanır.
     *
     * @param ticketId hedef bilet ID
     * @param userId istek yapan kullanıcı
     * @param roles kullanıcının rolleri
     * @return yorum listesi
     * @throws ResponseStatusException 403 — bilete erişim yoksa
     */
    public List<Comment> getCommentsByTicketId(Long ticketId, String userId, List<String> roles) {
        log.debug("Yorum listeleme işlemi. Bilet ID: {}, Kullanıcı: {}", ticketId, userId);

        // Yorumlari dondurmeden once bilet goruntuleme yetkisi dogrulanir.
        ticketService.getTicketWithAuth(ticketId, userId, roles);

        List<Comment> allComments = commentRepository.findByTicketIdOrderByCreatedAtAsc(ticketId);
        log.debug("Bilet ID: {} için veritabanından {} adet yorum çekildi.", ticketId, allComments.size());

        // Müşteri ekraninda dahili notlar filtrelenerek sadece disa acik yorumlar dondurulur.
        boolean isOnlyCustomer = roles.contains("CUSTOMER") && !roles.contains("AGENT") && !roles.contains("AGENT_ADMIN");
        if (isOnlyCustomer) {
            log.debug("Müşteri filtresi uygulanıyor: Dahili yorumlar gizleniyor.");
            return allComments.stream()
                    .filter(c -> "EXTERNAL".equals(c.getType()))
                    .collect(Collectors.toList());
        }

        return allComments;
    }
}
