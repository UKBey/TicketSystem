package com.ticketsystem.it_service_backend.service;

import com.ticketsystem.it_service_backend.entity.Comment;
import com.ticketsystem.it_service_backend.entity.Ticket;
import com.ticketsystem.it_service_backend.repository.CommentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CommentService {

    private final CommentRepository commentRepository;
    private final TicketService ticketService;

    @Transactional
    public Comment addComment(Long ticketId, String message, String type, String userId, List<String> roles) {
        // Sıkı Yetki Kontrolü (Agent=Assignee, Customer=Owner)
        Ticket ticket = ticketService.validateMutationAccess(ticketId, userId, roles);

        // Müşteriler sadece EXTERNAL (genel) yorum yapabilir
        boolean isOnlyCustomer = roles.contains("CUSTOMER") && !roles.contains("AGENT") && !roles.contains("MANAGER");
        if (isOnlyCustomer && "INTERNAL".equals(type)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Müşteriler sadece genel yorum ekleyebilir.");
        }

        Comment comment = Comment.builder()
                .ticket(ticket)
                .message(message)
                .type(type != null ? type : "EXTERNAL")
                .authorId(userId)
                .build();

        return commentRepository.save(comment);
    }

    public List<Comment> getCommentsByTicketId(Long ticketId, String userId, List<String> roles) {
        // Bileti görme yetkisi kontrolü
        ticketService.getTicketWithAuth(ticketId, userId, roles);

        List<Comment> allComments = commentRepository.findByTicketIdOrderByCreatedAtAsc(ticketId);

        // Eğer kullanıcı sadece CUSTOMER ise INTERNAL (dahili) yorumları gizle
        boolean isOnlyCustomer = roles.contains("CUSTOMER") && !roles.contains("AGENT") && !roles.contains("MANAGER");
        if (isOnlyCustomer) {
            return allComments.stream()
                    .filter(c -> "EXTERNAL".equals(c.getType()))
                    .collect(Collectors.toList());
        }

        return allComments;
    }
}
