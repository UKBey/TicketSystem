package com.ticketsystem.it_service_backend.service;

import com.ticketsystem.it_service_backend.entity.Comment;
import com.ticketsystem.it_service_backend.entity.Ticket;
import com.ticketsystem.it_service_backend.repository.CommentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import lombok.extern.log4j.Log4j2;

@Log4j2
@Service
@RequiredArgsConstructor
public class CommentService {

    private final CommentRepository commentRepository;
    private final TicketService ticketService;
    private final NotificationService notificationService;

    private final ConcurrentHashMap<String, Instant> lastCommentTime = new ConcurrentHashMap<>();
    private static final long COMMENT_COOLDOWN_SECONDS = 5;

    @Transactional
    public Comment addComment(Long ticketId, String message, String type, String userId, List<String> roles) {
        log.info("Yorum ekleme işlemi. Bilet ID: {}, Kullanıcı: {}, Tip: {}", ticketId, userId, type);

        Instant last = lastCommentTime.get(userId);
        if (last != null && Instant.now().isBefore(last.plusSeconds(COMMENT_COOLDOWN_SECONDS))) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(429), "Too many requests. Please wait before sending another comment.");
        }

        // Yorum ekleme, mutasyon yetkisi denetiminden gecmeden ilerlemez.
        Ticket ticket = ticketService.validateMutationAccess(ticketId, userId, roles);

        // Sadece CUSTOMER olan kullanici dahili not birakamaz.
        boolean isOnlyCustomer = roles.contains("CUSTOMER") && !roles.contains("AGENT") && !roles.contains("AGENT_ADMIN");
        if (isOnlyCustomer && "INTERNAL".equals(type)) {
            log.warn("Yorum reddedildi: Müşteri (ID: {}) dahili yorum eklemeye çalıştı.", userId);
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Müşteriler sadece genel yorum ekleyebilir.");
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

        // Musteri yaniti bekleme durumunu bozdugunda bilet tekrar calisma durumuna cekilir.
        if ("WAITING_FOR_CUSTOMER".equals(ticket.getStatus()) && ticket.getCustomerId().equals(userId)) {
            log.info("Müşteri yanıtı algılandı. Bilet statüsü WAITING_FOR_CUSTOMER'dan IN_PROGRESS'e çekiliyor.");
            ticketService.updateTicketStatus(ticketId, "IN_PROGRESS", userId, roles);
        }

        return savedComment;
    }

    public List<Comment> getCommentsByTicketId(Long ticketId, String userId, List<String> roles) {
        log.info("Yorum listeleme işlemi. Bilet ID: {}, Kullanıcı: {}", ticketId, userId);

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
