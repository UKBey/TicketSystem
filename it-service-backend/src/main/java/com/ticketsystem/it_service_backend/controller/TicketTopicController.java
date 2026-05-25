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
 * Bir ürüne bağlı talep konularının (topic) CRUD REST kontrolcüsü.
 *
 * <p>Listeleme açıktır; oluşturma/güncelleme/silme yalnızca {@code AGENT_ADMIN} ve
 * {@code MANAGER} rolleri tarafından yapılır. İş kuralları {@link TicketTopicService}'tedir.
 */
@Log4j2
@Tag(name = "Talep Konusu Yönetimi", description = "Bir ürüne bağlı talep konularının CRUD işlemleri")
@RestController
@RequiredArgsConstructor
public class TicketTopicController {

    private final TicketTopicService topicService;

    /**
     * Bir ürüne tanımlı talep konularını listeler; varsayılan olarak yalnızca aktif kayıtlar döner.
     *
     * @param productId ürün kimliği
     * @param includeInactive {@code true} ise pasif konular da dahil edilir
     * @return talep konusu DTO listesi
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
     * Belirtilen ürün altında yeni bir talep konusu oluşturur.
     *
     * @param productId ürün kimliği
     * @param body konu adı ve aktiflik bayrağı
     * @return oluşturulan talep konusu DTO'su
     */
    @Operation(summary = "Yeni talep konusu oluştur")
    @PostMapping("/api/v1/products/{productId}/topics")
    @PreAuthorize("hasAnyRole('AGENT_ADMIN', 'MANAGER')")
    public ResponseEntity<TicketTopicDTO> create(
            @PathVariable Long productId,
            @Valid @RequestBody TicketTopicDTO body) {
        log.info("Talep konusu oluşturma isteği. Ürün: {}, Ad: {}", productId, body.getName());
        TicketTopic created = topicService.create(productId, body.getName(), body.getIsActive());
        return ResponseEntity.ok(TicketTopicDTO.fromEntity(created));
    }

    /**
     * Mevcut bir talep konusunun adını veya aktiflik durumunu günceller.
     *
     * @param id güncellenecek konunun kimliği
     * @param body yeni ad ve aktiflik bayrağı
     * @return güncellenmiş talep konusu DTO'su
     */
    @Operation(summary = "Talep konusunu güncelle (ad veya aktiflik)")
    @PutMapping("/api/v1/topics/{id}")
    @PreAuthorize("hasAnyRole('AGENT_ADMIN', 'MANAGER')")
    public ResponseEntity<TicketTopicDTO> update(
            @PathVariable Long id,
            @Valid @RequestBody TicketTopicDTO body) {
        log.info("Talep konusu güncelleme isteği. ID: {}", id);
        TicketTopic updated = topicService.update(id, body.getName(), body.getIsActive());
        return ResponseEntity.ok(TicketTopicDTO.fromEntity(updated));
    }

    /**
     * Talep konusunu kalıcı olarak siler.
     *
     * @param id silinecek konunun kimliği
     * @return {@code 204 No Content}
     */
    @Operation(summary = "Talep konusunu sil")
    @DeleteMapping("/api/v1/topics/{id}")
    @PreAuthorize("hasAnyRole('AGENT_ADMIN', 'MANAGER')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        log.info("Talep konusu silme isteği. ID: {}", id);
        topicService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
