package com.ticketsystem.it_service_backend.controller;

import com.ticketsystem.it_service_backend.dto.KnownIssueDTO;
import com.ticketsystem.it_service_backend.entity.KnownIssue;
import com.ticketsystem.it_service_backend.service.KnownIssueService;
import com.ticketsystem.it_service_backend.util.JwtUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * "Sıkça karşılaşılan sorunlar" bilgi tabanı endpoint'leri.
 *
 * <p>Listeleme ve detay: kullanıcı ürüne yetkili olmalı (AGENT_ADMIN/MANAGER her şeyi görür).
 * Yazma operasyonları: yalnızca AGENT_ADMIN ve MANAGER.
 */
@Log4j2
@Tag(name = "Sıkça Karşılaşılan Sorunlar", description = "Ürüne (ve opsiyonel topic'e) bağlı bilgi tabanı CRUD'u")
@RestController
@RequiredArgsConstructor
public class KnownIssueController {

    private final KnownIssueService knownIssueService;

    @Operation(summary = "Bir ürünün sıkça karşılaşılan sorunlarını listele",
            description = "Varsayılan olarak yalnızca aktif kayıtlar gelir. `includeInactive=true` pasif olanları da dahil eder. "
                    + "`topicId` parametresiyle belirli bir topic'e filtrelenebilir.")
    @GetMapping("/api/v1/products/{productId}/known-issues")
    public ResponseEntity<List<KnownIssueDTO>> listByProduct(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long productId,
            @RequestParam(name = "topicId", required = false) Long topicId,
            @RequestParam(name = "includeInactive", defaultValue = "false") boolean includeInactive) {
        String userId = jwt.getSubject();
        List<String> roles = JwtUtils.extractRoles(jwt);
        List<KnownIssue> items = knownIssueService.listByProduct(productId, topicId, !includeInactive, userId, roles);
        return ResponseEntity.ok(items.stream().map(KnownIssueDTO::fromEntity).toList());
    }

    @Operation(summary = "Tek bir sıkça karşılaşılan sorun kaydını getir")
    @GetMapping("/api/v1/known-issues/{id}")
    public ResponseEntity<KnownIssueDTO> getOne(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long id) {
        String userId = jwt.getSubject();
        List<String> roles = JwtUtils.extractRoles(jwt);
        KnownIssue item = knownIssueService.getById(id, userId, roles);
        return ResponseEntity.ok(KnownIssueDTO.fromEntity(item));
    }

    @Operation(summary = "Yeni sıkça karşılaşılan sorun oluştur")
    @PostMapping("/api/v1/products/{productId}/known-issues")
    @PreAuthorize("hasAnyRole('AGENT_ADMIN', 'MANAGER')")
    public ResponseEntity<KnownIssueDTO> create(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long productId,
            @Valid @RequestBody KnownIssueDTO body) {
        String createdBy = jwt.getSubject();
        log.info("Sıkça karşılaşılan sorun oluşturma isteği. Ürün: {}, Başlık: {}", productId, body.getTitle());
        KnownIssue created = knownIssueService.create(
                productId, body.getTopicId(), body.getTitle(), body.getContent(),
                body.getIsActive(), createdBy);
        return ResponseEntity.ok(KnownIssueDTO.fromEntity(created));
    }

    @Operation(summary = "Sıkça karşılaşılan sorun kaydını güncelle")
    @PutMapping("/api/v1/known-issues/{id}")
    @PreAuthorize("hasAnyRole('AGENT_ADMIN', 'MANAGER')")
    public ResponseEntity<KnownIssueDTO> update(
            @PathVariable Long id,
            @Valid @RequestBody KnownIssueDTO body) {
        log.info("Sıkça karşılaşılan sorun güncelleme isteği. ID: {}", id);
        KnownIssue updated = knownIssueService.update(id, body.getTopicId(), body.getTitle(), body.getContent(), body.getIsActive());
        return ResponseEntity.ok(KnownIssueDTO.fromEntity(updated));
    }

    @Operation(summary = "Sıkça karşılaşılan sorun kaydını sil")
    @DeleteMapping("/api/v1/known-issues/{id}")
    @PreAuthorize("hasAnyRole('AGENT_ADMIN', 'MANAGER')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        log.info("Sıkça karşılaşılan sorun silme isteği. ID: {}", id);
        knownIssueService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
