package com.ticketsystem.it_service_backend.controller;

import com.ticketsystem.it_service_backend.dto.TicketTopicDTO;
import com.ticketsystem.it_service_backend.entity.TicketTopic;
import com.ticketsystem.it_service_backend.service.TicketTopicService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Log4j2
@Tag(name = "Talep Konusu Yönetimi", description = "Bir ürüne bağlı talep konularının CRUD işlemleri")
@RestController
@RequiredArgsConstructor
public class TicketTopicController {

    private final TicketTopicService topicService;

    @Operation(summary = "Bir ürünün talep konularını listele",
            description = "Varsayılan olarak yalnızca aktif konuları döner. `includeInactive=true` ile pasif olanlar da gelir.")
    @GetMapping("/api/products/{productId}/topics")
    public ResponseEntity<List<TicketTopicDTO>> listByProduct(
            @PathVariable Long productId,
            @RequestParam(name = "includeInactive", defaultValue = "false") boolean includeInactive) {
        List<TicketTopic> topics = topicService.listByProduct(productId, !includeInactive);
        return ResponseEntity.ok(topics.stream().map(TicketTopicDTO::fromEntity).toList());
    }

    @Operation(summary = "Yeni talep konusu oluştur")
    @PostMapping("/api/products/{productId}/topics")
    @PreAuthorize("hasRole('AGENT_ADMIN')")
    public ResponseEntity<TicketTopicDTO> create(
            @PathVariable Long productId,
            @Valid @RequestBody TicketTopicDTO body) {
        log.info("Talep konusu oluşturma isteği. Ürün: {}, Ad: {}", productId, body.getName());
        TicketTopic created = topicService.create(productId, body.getName(), body.getIsActive());
        return ResponseEntity.ok(TicketTopicDTO.fromEntity(created));
    }

    @Operation(summary = "Talep konusunu güncelle (ad veya aktiflik)")
    @PutMapping("/api/topics/{id}")
    @PreAuthorize("hasRole('AGENT_ADMIN')")
    public ResponseEntity<TicketTopicDTO> update(
            @PathVariable Long id,
            @Valid @RequestBody TicketTopicDTO body) {
        log.info("Talep konusu güncelleme isteği. ID: {}", id);
        TicketTopic updated = topicService.update(id, body.getName(), body.getIsActive());
        return ResponseEntity.ok(TicketTopicDTO.fromEntity(updated));
    }

    @Operation(summary = "Talep konusunu sil")
    @DeleteMapping("/api/topics/{id}")
    @PreAuthorize("hasRole('AGENT_ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        log.info("Talep konusu silme isteği. ID: {}", id);
        topicService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
