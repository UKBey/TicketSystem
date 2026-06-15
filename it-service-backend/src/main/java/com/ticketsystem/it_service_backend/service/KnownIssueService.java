package com.ticketsystem.it_service_backend.service;

import com.ticketsystem.it_service_backend.dto.KnownIssueDTO;
import com.ticketsystem.it_service_backend.entity.KnownIssue;
import com.ticketsystem.it_service_backend.entity.User;
import com.ticketsystem.it_service_backend.repository.KnownIssueRepository;
import com.ticketsystem.it_service_backend.repository.ProductRepository;
import com.ticketsystem.it_service_backend.repository.TicketTopicRepository;
import com.ticketsystem.it_service_backend.repository.UserRepository;
import com.ticketsystem.it_service_backend.util.AuthRoles;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * Manages known issues.
 *
 * <p>Read access: the user must be authorized for the product; admin/manager see every product.
 * Management (create/update/delete): restricted to ADMIN and MANAGER. This rule is
 * enforced at the controller level via {@code @PreAuthorize}.
 */
@Log4j2
@Service
@RequiredArgsConstructor
public class KnownIssueService {

    private final KnownIssueRepository knownIssueRepository;
    private final ProductRepository productRepository;
    private final TicketTopicRepository topicRepository;
    private final UserRepository userRepository;

    // --------------------------------------------------------------------
    // Read
    // --------------------------------------------------------------------

    /**
     * Returns known issue records for a product (optionally narrowed to a specific topic)
     * ordered from newest to oldest. Product access is verified for customer/agent users;
     * admin/manager bypass the check.
     *
     * @param productId target product ID
     * @param topicId optional topic filter
     * @param activeOnly when {@code true}, returns only active records
     * @param userId user whose access is being checked (ignored for admin/manager)
     * @param roles role list of the user
     * @return list of known issues (may be empty)
     * @throws ResponseStatusException 404 if the product is missing, 403 if the user lacks access
     */
    @Transactional(readOnly = true)
    public List<KnownIssue> listByProduct(Long productId, Long topicId, boolean activeOnly,
                                          String userId, List<String> roles) {
        ensureProductExists(productId);
        ensureProductAccess(productId, userId, roles);

        if (topicId != null) {
            return activeOnly
                    ? knownIssueRepository.findByProductIdAndTopicIdAndIsActiveTrueOrderByCreatedAtDesc(productId, topicId)
                    : knownIssueRepository.findByProductIdAndTopicIdOrderByCreatedAtDesc(productId, topicId);
        }
        return activeOnly
                ? knownIssueRepository.findByProductIdAndIsActiveTrueOrderByCreatedAtDesc(productId)
                : knownIssueRepository.findByProductIdOrderByCreatedAtDesc(productId);
    }

    /**
     * Returns the active known-issue records relevant to a ticket, newest first, as
     * DTOs. "Relevant" means: same product, and either product-wide (no topic) or
     * matching the ticket's topic. Intended for internal/service-to-service consumers
     * (e.g. the LLM context bundle) — no per-user access check is applied.
     *
     * @param productId the ticket's product ID (null yields an empty list)
     * @param topicId the ticket's topic ID (may be null)
     * @return matching active known-issue DTOs (possibly empty)
     */
    @Transactional(readOnly = true)
    public List<KnownIssueDTO> getActiveForTicket(Long productId, Long topicId) {
        if (productId == null) return List.of();
        return knownIssueRepository.findByProductIdAndIsActiveTrueOrderByCreatedAtDesc(productId).stream()
                .filter(ki -> ki.getTopicId() == null || ki.getTopicId().equals(topicId))
                .map(KnownIssueDTO::fromEntity)
                .toList();
    }

    /**
     * Returns a single known issue record; the caller's product access is verified.
     *
     * @param id record ID
     * @param userId user whose access is being checked
     * @param roles role list of the user
     * @return the matching {@link KnownIssue}
     * @throws ResponseStatusException 404 if not found, 403 if the user lacks product access
     */
    @Transactional(readOnly = true)
    public KnownIssue getById(Long id, String userId, List<String> roles) {
        KnownIssue issue = findOrThrow(id);
        ensureProductAccess(issue.getProductId(), userId, roles);
        return issue;
    }

    // --------------------------------------------------------------------
    // Write
    // --------------------------------------------------------------------

    /**
     * Creates a new known issue record.
     *
     * <p>Title and content are bilingual (tr/en); at least one language must be
     * provided for each. When a topic is supplied, it must belong to the same
     * product. Values are trimmed (blank collapses to {@code null}) and length
     * limits (title 255, content 10,000) are enforced per language.
     *
     * @param productId ID of the owning product
     * @param topicId optional topic ID (must belong to the product when set)
     * @param titleTr title (Turkish, optional if titleEn present)
     * @param titleEn title (English, optional if titleTr present)
     * @param contentTr content (Turkish, optional if contentEn present)
     * @param contentEn content (English, optional if contentTr present)
     * @param isActive active flag (defaults to {@code true} when {@code null})
     * @param createdBy ID of the creating user (for audit)
     * @return the persisted record
     * @throws ResponseStatusException 404 if product/topic missing, 400 on validation errors
     */
    @Transactional
    public KnownIssue create(Long productId, Long topicId, String titleTr, String titleEn,
                             String contentTr, String contentEn, Boolean isActive, String createdBy) {
        ensureProductExists(productId);
        validateTopicBelongsToProduct(productId, topicId);
        String tTr = normalize(titleTr);
        String tEn = normalize(titleEn);
        String cTr = normalize(contentTr);
        String cEn = normalize(contentEn);
        validateTitle(tTr, tEn);
        validateContent(cTr, cEn);

        KnownIssue issue = KnownIssue.builder()
                .productId(productId)
                .topicId(topicId)
                .titleTr(tTr)
                .titleEn(tEn)
                .contentTr(cTr)
                .contentEn(cEn)
                .isActive(isActive == null ? Boolean.TRUE : isActive)
                .createdBy(createdBy)
                .build();
        KnownIssue saved = knownIssueRepository.save(issue);
        log.info("Sıkça karşılaşılan sorun oluşturuldu. ID: {}, Ürün: {}", saved.getId(), productId);
        return saved;
    }

    /**
     * Partial update; the title or content is only touched when at least one of its
     * language variants is supplied (and the "at least one language" rule still holds
     * after the change). If the topic changes, it must belong to the same product.
     *
     * @param id record ID to update
     * @param topicId new topic (optional)
     * @param titleTr new Turkish title (optional)
     * @param titleEn new English title (optional)
     * @param contentTr new Turkish content (optional)
     * @param contentEn new English content (optional)
     * @param isActive new active flag (optional)
     * @return the updated record
     * @throws ResponseStatusException 404 if record/topic missing, 400 on validation errors
     */
    @Transactional
    public KnownIssue update(Long id, Long topicId, String titleTr, String titleEn,
                             String contentTr, String contentEn, Boolean isActive) {
        KnownIssue existing = findOrThrow(id);
        if (topicId != null) {
            validateTopicBelongsToProduct(existing.getProductId(), topicId);
            existing.setTopicId(topicId);
        }
        if (titleTr != null || titleEn != null) {
            String tTr = normalize(titleTr);
            String tEn = normalize(titleEn);
            validateTitle(tTr, tEn);
            existing.setTitleTr(tTr);
            existing.setTitleEn(tEn);
        }
        if (contentTr != null || contentEn != null) {
            String cTr = normalize(contentTr);
            String cEn = normalize(contentEn);
            validateContent(cTr, cEn);
            existing.setContentTr(cTr);
            existing.setContentEn(cEn);
        }
        if (isActive != null) {
            existing.setIsActive(isActive);
        }
        KnownIssue saved = knownIssueRepository.save(existing);
        log.info("Sıkça karşılaşılan sorun güncellendi. ID: {}", saved.getId());
        return saved;
    }

    /**
     * Deletes the known issue record.
     *
     * @param id record ID to delete
     * @throws ResponseStatusException 404 if the record is not found
     */
    @Transactional
    public void delete(Long id) {
        KnownIssue existing = findOrThrow(id);
        knownIssueRepository.delete(existing);
        log.info("Sıkça karşılaşılan sorun silindi. ID: {}", id);
    }

    // --------------------------------------------------------------------
    // Helpers
    // --------------------------------------------------------------------

    private KnownIssue findOrThrow(Long id) {
        return knownIssueRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "error.known-issue.not.found"));
    }

    private void ensureProductExists(Long productId) {
        if (!productRepository.existsById(productId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "error.product.not.found");
        }
    }

    /**
     * Product access check for a user. Admin/manager bypass the check; other roles
     * must have the product in their authorizedProducts list.
     */
    private void ensureProductAccess(Long productId, String userId, List<String> roles) {
        if (AuthRoles.isGlobal(roles)) return;
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "error.forbidden");
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "error.user.not.found"));
        boolean authorized = user.getAuthorizedProducts().stream()
                .anyMatch(p -> p.getId().equals(productId));
        if (!authorized) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "error.product.access.forbidden");
        }
    }

    private void validateTopicBelongsToProduct(Long productId, Long topicId) {
        if (topicId == null) return;
        topicRepository.findById(topicId).ifPresentOrElse(
                topic -> {
                    if (!topic.getProductId().equals(productId)) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "error.known-issue.topic.mismatch");
                    }
                },
                () -> { throw new ResponseStatusException(HttpStatus.NOT_FOUND, "error.topic.not.found"); }
        );
    }

    /** Trims and converts blank to {@code null} so "cleared" and "absent" collapse to one state. */
    private String normalize(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /** At least one language variant must be present; each variant must respect the length limit. */
    private void validateTitle(String titleTr, String titleEn) {
        if (titleTr == null && titleEn == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "error.known-issue.title.empty");
        }
        if ((titleTr != null && titleTr.length() > 255) || (titleEn != null && titleEn.length() > 255)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "error.known-issue.title.too-long");
        }
    }

    private void validateContent(String contentTr, String contentEn) {
        if (contentTr == null && contentEn == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "error.known-issue.content.empty");
        }
        if ((contentTr != null && contentTr.length() > 10000) || (contentEn != null && contentEn.length() > 10000)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "error.known-issue.content.too-long");
        }
    }
}
