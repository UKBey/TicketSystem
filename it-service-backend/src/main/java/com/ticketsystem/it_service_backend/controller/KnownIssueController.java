package com.ticketsystem.it_service_backend.controller;

import com.ticketsystem.it_service_backend.dto.KnownIssueDTO;
import com.ticketsystem.it_service_backend.entity.KnownIssue;
import com.ticketsystem.it_service_backend.service.KnownIssueService;
import com.ticketsystem.it_service_backend.util.JwtUtils;
import com.ticketsystem.it_service_backend.util.Pageables;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for the "known issues" knowledge base.
 *
 * <p>Listing and detail: the user must be authorized for the product ({@code ADMIN}/{@code MANAGER}
 * see everything). Write operations are restricted to {@code LEAD_AGENT} and {@code ADMIN};
 * business rules live in {@link KnownIssueService}.
 */
@Log4j2
@Tag(name = "Sıkça Karşılaşılan Sorunlar", description = "Ürüne (ve opsiyonel topic'e) bağlı bilgi tabanı CRUD'u")
@RestController
@RequiredArgsConstructor
public class KnownIssueController {

    private final KnownIssueService knownIssueService;

    /**
     * Lists the known issues for a product (with an optional topic filter).
     *
     * @param productId product identifier
     * @param topicId optional topic filter
     * @param includeInactive when {@code true}, inactive records are also included
     * @return list of DTOs filtered by role-based authorization
     */
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

    /**
     * Paginated variant of {@link #listByProduct} for the management screen.
     *
     * @param productId product identifier
     * @param topicId optional topic filter
     * @param includeInactive when {@code true}, inactive records are also included
     * @param page zero-based page index
     * @param size page size
     * @return a page of DTOs filtered by role-based authorization
     */
    @Operation(summary = "Bir ürünün sıkça karşılaşılan sorunlarını sayfalı listele")
    @GetMapping("/api/v1/products/{productId}/known-issues/paged")
    public ResponseEntity<Page<KnownIssueDTO>> listByProductPaged(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long productId,
            @RequestParam(name = "topicId", required = false) Long topicId,
            @RequestParam(name = "includeInactive", defaultValue = "false") boolean includeInactive,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "10") int size) {
        String userId = jwt.getSubject();
        List<String> roles = JwtUtils.extractRoles(jwt);
        Page<KnownIssue> items = knownIssueService.listByProductPaged(
                productId, topicId, !includeInactive, userId, roles,
                Pageables.of(page, size, "createdAt", "desc"));
        return ResponseEntity.ok(items.map(KnownIssueDTO::fromEntity));
    }

    /**
     * Returns a single known issue record; the user must be authorized for the product.
     *
     * @param id record identifier
     * @return the matching DTO
     */
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

    /**
     * Creates a new known issue record under the specified product.
     *
     * @param productId product identifier
     * @param body title, content, optional topic and active flag
     * @return DTO of the created record
     */
    @Operation(summary = "Yeni sıkça karşılaşılan sorun oluştur")
    @PostMapping("/api/v1/products/{productId}/known-issues")
    @PreAuthorize("hasAnyRole('LEAD_AGENT', 'ADMIN')")
    public ResponseEntity<KnownIssueDTO> create(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long productId,
            @Valid @RequestBody KnownIssueDTO body) {
        String createdBy = jwt.getSubject();
        log.info("Sıkça karşılaşılan sorun oluşturma isteği. Ürün: {}", productId);
        KnownIssue created = knownIssueService.create(
                productId, body.getTopicId(), body.getTitleTr(), body.getTitleEn(),
                body.getContentTr(), body.getContentEn(), body.getIsActive(), createdBy);
        return ResponseEntity.ok(KnownIssueDTO.fromEntity(created));
    }

    /**
     * Updates the fields of an existing known issue record.
     *
     * @param id identifier of the record to update
     * @param body new topic, title, content and active value
     * @return DTO of the updated record
     */
    @Operation(summary = "Sıkça karşılaşılan sorun kaydını güncelle")
    @PutMapping("/api/v1/known-issues/{id}")
    @PreAuthorize("hasAnyRole('LEAD_AGENT', 'ADMIN')")
    public ResponseEntity<KnownIssueDTO> update(
            @PathVariable Long id,
            @Valid @RequestBody KnownIssueDTO body) {
        log.info("Sıkça karşılaşılan sorun güncelleme isteği. ID: {}", id);
        KnownIssue updated = knownIssueService.update(id, body.getTopicId(),
                body.getTitleTr(), body.getTitleEn(), body.getContentTr(), body.getContentEn(), body.getIsActive());
        return ResponseEntity.ok(KnownIssueDTO.fromEntity(updated));
    }

    /**
     * Permanently deletes a known issue record.
     *
     * @param id identifier of the record to delete
     * @return {@code 204 No Content}
     */
    @Operation(summary = "Sıkça karşılaşılan sorun kaydını sil")
    @DeleteMapping("/api/v1/known-issues/{id}")
    @PreAuthorize("hasAnyRole('LEAD_AGENT', 'ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        log.info("Sıkça karşılaşılan sorun silme isteği. ID: {}", id);
        knownIssueService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
