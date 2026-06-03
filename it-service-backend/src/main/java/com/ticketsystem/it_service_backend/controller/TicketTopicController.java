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

/**
 * REST controller for the CRUD of ticket topics that belong to a product.
 *
 * <p>Listing is open; creation, update and deletion are restricted to the
 * {@code LEAD_AGENT} and {@code ADMIN} roles. Business rules live in {@link TicketTopicService}.
 */
@Log4j2
@Tag(name = "Talep Konusu Yönetimi", description = "Bir ürüne bağlı talep konularının CRUD işlemleri")
@RestController
@RequiredArgsConstructor
public class TicketTopicController {

    private final TicketTopicService topicService;

    /**
     * Lists the topics defined for a product; by default only active records are returned.
     *
     * @param productId product identifier
     * @param includeInactive when {@code true}, inactive topics are also included
     * @return list of ticket topic DTOs
     */
    @Operation(summary = "Bir ürünün talep konularını listele",
            description = "Varsayılan olarak yalnızca aktif konuları döner. `includeInactive=true` ile pasif olanlar da gelir.")
    @GetMapping("/api/v1/products/{productId}/topics")
    public ResponseEntity<List<TicketTopicDTO>> listByProduct(
            @PathVariable Long productId,
            @RequestParam(name = "includeInactive", defaultValue = "false") boolean includeInactive) {
        List<TicketTopic> topics = topicService.listByProduct(productId, !includeInactive);
        return ResponseEntity.ok(topics.stream().map(TicketTopicDTO::fromEntity).toList());
    }

    /**
     * Creates a new ticket topic under the specified product.
     *
     * @param productId product identifier
     * @param body topic name and active flag
     * @return DTO of the created topic
     */
    @Operation(summary = "Yeni talep konusu oluştur")
    @PostMapping("/api/v1/products/{productId}/topics")
    @PreAuthorize("hasAnyRole('LEAD_AGENT', 'ADMIN')")
    public ResponseEntity<TicketTopicDTO> create(
            @PathVariable Long productId,
            @Valid @RequestBody TicketTopicDTO body) {
        log.info("Talep konusu oluşturma isteği. Ürün: {}, Ad: {}", productId, body.getName());
        TicketTopic created = topicService.create(productId, body.getName(), body.getIsActive());
        return ResponseEntity.ok(TicketTopicDTO.fromEntity(created));
    }

    /**
     * Updates the name or active status of an existing topic.
     *
     * @param id identifier of the topic to update
     * @param body new name and active flag
     * @return DTO of the updated topic
     */
    @Operation(summary = "Talep konusunu güncelle (ad veya aktiflik)")
    @PutMapping("/api/v1/topics/{id}")
    @PreAuthorize("hasAnyRole('LEAD_AGENT', 'ADMIN')")
    public ResponseEntity<TicketTopicDTO> update(
            @PathVariable Long id,
            @Valid @RequestBody TicketTopicDTO body) {
        log.info("Talep konusu güncelleme isteği. ID: {}", id);
        TicketTopic updated = topicService.update(id, body.getName(), body.getIsActive());
        return ResponseEntity.ok(TicketTopicDTO.fromEntity(updated));
    }

    /**
     * Permanently deletes the topic.
     *
     * @param id identifier of the topic to delete
     * @return {@code 204 No Content}
     */
    @Operation(summary = "Talep konusunu sil")
    @DeleteMapping("/api/v1/topics/{id}")
    @PreAuthorize("hasAnyRole('LEAD_AGENT', 'ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        log.info("Talep konusu silme isteği. ID: {}", id);
        topicService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
