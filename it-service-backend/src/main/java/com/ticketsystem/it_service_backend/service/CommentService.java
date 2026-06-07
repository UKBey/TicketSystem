package com.ticketsystem.it_service_backend.service;

import com.ticketsystem.it_service_backend.dto.CommentDTO;
import com.ticketsystem.it_service_backend.entity.Comment;
import com.ticketsystem.it_service_backend.entity.Ticket;
import com.ticketsystem.it_service_backend.entity.User;
import com.ticketsystem.it_service_backend.repository.CommentRepository;
import com.ticketsystem.it_service_backend.repository.UserRepository;
import com.ticketsystem.it_service_backend.util.AuthRoles;
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

import lombok.extern.log4j.Log4j2;

/**
 * Handles ticket comment creation and listing.
 *
 * <p>EXTERNAL comments are visible to both parties; INTERNAL comments are visible
 * only to agents. A simple per-user in-memory cooldown and a maximum-length check
 * are applied (config: {@code app.comments.*}). After persistence,
 * {@link NotificationService} produces a notification and a STOMP broadcast is
 * sent on the ticket topic. When a customer comments on a WAITING_FOR_CUSTOMER
 * ticket, the status is automatically moved back to IN_PROGRESS.
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
    @Value("${app.comments.cooldown-seconds:3}")
    private long cooldownSeconds;

    @Value("${app.comments.max-length:500}")
    private int maxMessageLength;

    /** Per-user comment cooldown in seconds (config-driven) — exposed so the client can mirror it. */
    public long getCooldownSeconds() {
        return cooldownSeconds;
    }

    /** Maximum comment length (config-driven). */
    public int getMaxMessageLength() {
        return maxMessageLength;
    }

    /**
     * Adds a new comment to a ticket.
     *
     * <p>Validations: length (config-driven), per-user cooldown, mutation access,
     * and INTERNAL comments only by agent roles. After persistence, a notification
     * and WebSocket event are triggered; when a customer comments on a
     * WAITING_FOR_CUSTOMER ticket, the status is moved to IN_PROGRESS.
     *
     * @param ticketId target ticket ID
     * @param message comment text
     * @param type comment type (EXTERNAL or INTERNAL); EXTERNAL when null/blank
     * @param userId user authoring the comment
     * @param roles role list of the user
     * @return the persisted {@link Comment}
     * @throws ResponseStatusException 400 on length, 429 on cooldown, 403 on authorization
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
        boolean isOnlyCustomer = roles.contains(AuthRoles.CUSTOMER) && !AuthRoles.isAgentLevel(roles);
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
     * Returns the ticket's comments in oldest-to-newest order. INTERNAL comments
     * are filtered out for customer-only callers. Ticket access is verified via
     * {@link TicketService}.
     *
     * @param ticketId target ticket ID
     * @param userId requesting user
     * @param roles role list of the user
     * @return list of comments
     * @throws ResponseStatusException 403 if the user has no ticket access
     */
    public List<Comment> getCommentsByTicketId(Long ticketId, String userId, List<String> roles) {
        log.debug("Yorum listeleme işlemi. Bilet ID: {}, Kullanıcı: {}", ticketId, userId);

        // Yorumlari dondurmeden once bilet goruntuleme yetkisi dogrulanir.
        ticketService.getTicketWithAuth(ticketId, userId, roles);

        List<Comment> allComments = commentRepository.findByTicketIdOrderByCreatedAtAsc(ticketId);
        log.debug("Bilet ID: {} için veritabanından {} adet yorum çekildi.", ticketId, allComments.size());

        // Müşteri ekraninda dahili notlar filtrelenerek sadece disa acik yorumlar dondurulur.
        boolean isOnlyCustomer = roles.contains(AuthRoles.CUSTOMER) && !AuthRoles.isAgentLevel(roles);
        if (isOnlyCustomer) {
            log.debug("Müşteri filtresi uygulanıyor: Dahili yorumlar gizleniyor.");
            return allComments.stream()
                    .filter(c -> "EXTERNAL".equals(c.getType()))
                    .toList();
        }

        return allComments;
    }
}
