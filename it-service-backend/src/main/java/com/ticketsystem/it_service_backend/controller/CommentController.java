package com.ticketsystem.it_service_backend.controller;

import com.ticketsystem.it_service_backend.dto.CommentDTO;
import com.ticketsystem.it_service_backend.entity.Comment;
import com.ticketsystem.it_service_backend.entity.User;
import com.ticketsystem.it_service_backend.repository.UserRepository;
import com.ticketsystem.it_service_backend.service.CommentService;
import com.ticketsystem.it_service_backend.util.JwtUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Log4j2
@Tag(name = "Yorum Yönetimi", description = "Biletlere yapılan yorumların (Comment) yönetimi")
@RestController
@RequestMapping("/api/tickets/{ticketId}/comments")
@RequiredArgsConstructor
public class CommentController {

    private final CommentService commentService;
    private final UserRepository userRepository;

    // Bilete yeni yorum ekler ve yetki kurallarini servis katmaninda uygular.
    @Operation(summary = "Bilete yorum ekle", description = "Belirli bir bilete (Ticket) yeni bir yorum ekler. Yetki kontrolü yapılır.")
    @PostMapping
    public ResponseEntity<CommentDTO> addComment(
            @PathVariable Long ticketId,
            @RequestBody Map<String, String> body,
            @AuthenticationPrincipal Jwt jwt) {

        String userId = jwt.getSubject();
        List<String> roles = JwtUtils.extractRoles(jwt);
        String message = body.get("message");
        String type = body.get("type");

        // Gelen istek bilgileri izlenebilirlik icin loglanir.
        log.info("Bilet ID: {} için yeni yorum ekleme isteği alındı. İstek atan Kullanıcı ID: {}, Yorum Tipi: {}",
                ticketId, userId, type);
        log.debug("Yorum ekleyen kullanıcının rolleri: {}, Mesaj içeriği: {}", roles, message);

        // Hatalar GlobalExceptionHandler tarafinda merkezi olarak cevaplanir.
        Comment comment = commentService.addComment(ticketId, message, type, userId, roles);

        // Kayit tamamlandiginda olusan yorum kimligi donus oncesi loglanir.
        log.info("Yorum başarıyla eklendi. Bilet ID: {}, Yeni Yorum ID: {}", ticketId, comment.getId());

        return ResponseEntity.ok(convertToDto(comment));
    }

    // Biletin yorum gecmisini, rol kurallarina gore filtrelenmis sekilde listeler.
    @Operation(summary = "Biletin yorumlarını listele", description = "Bilet ID'sine göre tüm yorumları getirir. Yetki kontrolü yapılır.")
    @GetMapping
    public ResponseEntity<List<CommentDTO>> getComments(
            @PathVariable Long ticketId,
            @AuthenticationPrincipal Jwt jwt) {

        String userId = jwt.getSubject();
        List<String> roles = JwtUtils.extractRoles(jwt);

        // Listeleme talebi ve cagriyi yapan kullanici bilgisi kayda gecirilir.
        log.info("Bilet ID: {} için yorumları listeleme isteği alındı. Kullanıcı ID: {}", ticketId, userId);

        // Yetki veya is kurali hatalari ortak exception katmanina birakilir.
        List<Comment> comments = commentService.getCommentsByTicketId(ticketId, userId, roles);

        log.debug("Bilet ID: {} için veritabanından {} adet yorum çekildi. Kontrol edilen roller: {}", ticketId,
                comments.size(), roles);

        List<CommentDTO> commentDTOs = comments.stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());

        // Donen yorum adedi yanit oncesi loglanir.
        log.info("Bilet ID: {} için toplam {} yorum başarıyla listelendi.", ticketId, commentDTOs.size());

        return ResponseEntity.ok(commentDTOs);
    }

    private CommentDTO convertToDto(Comment comment) {
        String authorName = comment.getAuthorId() != null
            ? userRepository.findById(comment.getAuthorId()).map(User::getFullName).orElse("Unknown")
            : "Unknown";
        return CommentDTO.fromEntity(comment, authorName);
    }
}
