package com.ticketsystem.it_service_backend.controller;

import com.ticketsystem.it_service_backend.dto.CommentDTO;
import com.ticketsystem.it_service_backend.entity.Comment;
import com.ticketsystem.it_service_backend.service.CommentService;
import com.ticketsystem.it_service_backend.util.JwtUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/tickets/{ticketId}/comments")
@RequiredArgsConstructor
public class CommentController {

    private final CommentService commentService;

    // 1. Bilete Yorum Ekle
    @PostMapping
    public ResponseEntity<CommentDTO> addComment(
            @PathVariable Long ticketId,
            @RequestBody Map<String, String> body,
            @AuthenticationPrincipal Jwt jwt) {
        
        String userId = jwt.getSubject();
        List<String> roles = JwtUtils.extractRoles(jwt);
        String message = body.get("message");
        String type = body.get("type"); // INTERNAL veya EXTERNAL

        Comment comment = commentService.addComment(ticketId, message, type, userId, roles);
        return ResponseEntity.ok(CommentDTO.fromEntity(comment));
    }

    // 2. Biletin Yorumlarını Listele
    @GetMapping
    public ResponseEntity<List<CommentDTO>> getComments(
            @PathVariable Long ticketId,
            @AuthenticationPrincipal Jwt jwt) {
        
        String userId = jwt.getSubject();
        List<String> roles = JwtUtils.extractRoles(jwt);

        return ResponseEntity.ok(commentService.getCommentsByTicketId(ticketId, userId, roles).stream()
                .map(CommentDTO::fromEntity)
                .collect(Collectors.toList()));
    }
}
