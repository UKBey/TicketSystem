package com.ticketsystem.it_service_backend.controller;

import com.ticketsystem.it_service_backend.dto.CommentDTO;
import com.ticketsystem.it_service_backend.dto.CommentRequestDTO;
import com.ticketsystem.it_service_backend.entity.Comment;
import com.ticketsystem.it_service_backend.entity.User;
import com.ticketsystem.it_service_backend.repository.UserRepository;
import com.ticketsystem.it_service_backend.service.CommentService;
import com.ticketsystem.it_service_backend.util.JwtUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.stream.Collectors;

@Log4j2
@Tag(name = "Yorum Yönetimi", description = "Biletlere yapılan müşteri yanıtları ve dahili notların yönetimi")
@RestController
@RequestMapping("/api/tickets/{ticketId}/comments")
@RequiredArgsConstructor
// S-6: defense-in-depth — SecurityConfig zaten anonim erisimi yasakliyor; method-level
// auth check'i kodda gorunur kilar. Rol bazli kontrol (kim yorum yazip okuyabilir)
// servis katmanindadir; burada yalniz authenticate olmayi zorunlu kiliyoruz.
@PreAuthorize("isAuthenticated()")
public class CommentController {

    private final CommentService commentService;
    private final UserRepository userRepository;

    // Bilete yeni yorum ekler ve yetki kurallarini servis katmaninda uygular.
    @Operation(summary = "Bilete yorum ekle",
            description = """
                    Belirtilen bilete yeni bir yorum ekler. Yorum tipleri:
                    - **EXTERNAL**: Müşteriye görünür yanıt. Tüm roller kullanabilir.
                    - **INTERNAL**: Sadece Agent ve Agent Admin görebilir. Müşteri bu yorumu göremez.
                    
                    Yetki kuralları:
                    - Müşteri yalnızca kendi biletine EXTERNAL yorum ekleyebilir
                    - Agent yalnızca üzerine atanan bilete yorum ekleyebilir
                    - Agent Admin tüm biletlere yorum ekleyebilir
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Yorum başarıyla eklendi",
                    content = @Content(schema = @Schema(implementation = CommentDTO.class))),
            @ApiResponse(responseCode = "403", description = "Bu bilete yorum ekleme yetkiniz yok"),
            @ApiResponse(responseCode = "404", description = "Bilet bulunamadı")
    })
    @PostMapping
    public ResponseEntity<CommentDTO> addComment(
            @Parameter(description = "Yorumun ekleneceği biletin ID'si", example = "42", required = true)
            @PathVariable Long ticketId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Yorum içeriği ve tipi",
                    content = @Content(schema = @Schema(implementation = CommentRequestDTO.class)))
            @Valid @RequestBody CommentRequestDTO body,
            @AuthenticationPrincipal Jwt jwt) {

        String userId = jwt.getSubject();
        List<String> roles = JwtUtils.extractRoles(jwt);
        String message = body.getMessage();
        String type = body.getType();

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
    @Operation(summary = "Biletin yorumlarını listele",
            description = """
                    Belirtilen biletin tüm yorumlarını kronolojik sırada getirir.
                    
                    **Filtreleme kuralları:**
                    - Müşteri yalnızca EXTERNAL tip yorumları görür
                    - Agent ve Agent Admin hem EXTERNAL hem INTERNAL yorumları görür
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Yorum listesi başarıyla döndü"),
            @ApiResponse(responseCode = "403", description = "Bu biletin yorumlarını görüntüleme yetkiniz yok"),
            @ApiResponse(responseCode = "404", description = "Bilet bulunamadı")
    })
    @GetMapping
    public ResponseEntity<List<CommentDTO>> getComments(
            @Parameter(description = "Yorumları listelenecek biletin ID'si", example = "42", required = true)
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
        User author = comment.getAuthorId() != null
            ? userRepository.findById(comment.getAuthorId()).orElse(null)
            : null;
        String authorName = author != null ? author.getFullName() : "Unknown";
        String authorRole = author != null ? author.getRole() : null;
        return CommentDTO.fromEntity(comment, authorName, authorRole);
    }
}
