package com.ticketsystem.it_service_backend.service;

import com.ticketsystem.it_service_backend.dto.CannedResponseDTO;
import com.ticketsystem.it_service_backend.entity.CannedResponse;
import com.ticketsystem.it_service_backend.entity.CannedResponseFavorite;
import com.ticketsystem.it_service_backend.repository.CannedResponseFavoriteRepository;
import com.ticketsystem.it_service_backend.repository.CannedResponseRepository;
import com.ticketsystem.it_service_backend.repository.ProductRepository;
import com.ticketsystem.it_service_backend.util.AuthRoles;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.ticketsystem.it_service_backend.entity.CannedResponse.*;

/**
 * Business logic for canned responses (quick replies).
 *
 * <p><b>Visibility</b> — an agent always sees their own {@code PERSONAL} templates plus every
 * {@code SHARED} template (optionally narrowed to a ticket's product). Customers never reach
 * this service (blocked at the controller via {@code @PreAuthorize}), so {@code INTERNAL}
 * templates cannot leak to them.
 *
 * <p><b>Management</b> — anyone (agent role) may create/edit/delete their own {@code PERSONAL}
 * templates. Only {@code ADMIN}/{@code MANAGER} may create/edit/delete {@code SHARED}
 * (team/product) templates. These rules are enforced here in addition to the coarse role gate
 * on the controller, because the controller cannot know whether a given template is personal
 * or shared, nor who owns it.
 */
@Log4j2
@Service
@RequiredArgsConstructor
public class CannedResponseService {

    private static final int TITLE_MAX = 150;
    private static final int SHORTCUT_MAX = 50;
    private static final int CONTENT_MAX = 2000;

    private final CannedResponseRepository repository;
    private final CannedResponseFavoriteRepository favoriteRepository;
    private final ProductRepository productRepository;

    // --------------------------------------------------------------------
    // Read
    // --------------------------------------------------------------------

    /**
     * Lists the canned responses visible to the user, optionally narrowed by ticket product,
     * scope, suited comment visibility and a free-text query. Each returned DTO carries the
     * caller's favorite flag.
     *
     * @param userId requesting agent's Keycloak subject
     * @param productId optional ticket product (picker context); {@code null} = management/all
     * @param scope optional exact scope filter ({@code PERSONAL}/{@code SHARED})
     * @param visibility optional comment-type filter; {@code EXTERNAL}/{@code INTERNAL} also
     *                   keep {@code BOTH} templates, since those suit either side
     * @param q optional case-insensitive search over title, shortcut and both content variants
     * @return matching templates, newest-updated first
     */
    @Transactional(readOnly = true)
    public List<CannedResponseDTO> listVisible(String userId, Long productId,
                                               String scope, String visibility, String q) {
        // Validate optional filters up front so a bad value yields 400, not a silent empty list.
        String scopeFilter = validateScopeFilter(scope);
        String visFilter = validateVisibilityFilter(visibility);
        String needle = (q == null || q.isBlank()) ? null : q.trim().toLowerCase();

        List<CannedResponse> base = (productId != null)
                ? repository.findVisibleForProduct(userId, productId)
                : repository.findVisibleToUser(userId);

        Set<Long> favoriteIds = new HashSet<>(favoriteRepository.findFavoriteIdsByUser(userId));

        return base.stream()
                .filter(c -> scopeFilter == null || scopeFilter.equals(c.getScope()))
                .filter(c -> matchesVisibility(c, visFilter))
                .filter(c -> matchesSearch(c, needle))
                .map(c -> CannedResponseDTO.fromEntity(c, favoriteIds.contains(c.getId())))
                .toList();
    }

    // --------------------------------------------------------------------
    // Write
    // --------------------------------------------------------------------

    /**
     * Creates a canned response owned by the calling agent. A missing scope defaults to
     * {@code PERSONAL} (the least-privileged option); creating a {@code SHARED} template
     * requires the admin/manager role.
     *
     * @throws ResponseStatusException 403 (shared without admin), 400 (validation), 404 (product)
     */
    @Transactional
    public CannedResponseDTO create(CannedResponseDTO dto, String userId, List<String> roles) {
        String scope = normalizeScope(dto.getScope());
        if (SCOPE_SHARED.equals(scope)) {
            requireAdmin(roles);
        }

        String title = requireValidTitle(dto.getTitle());
        String tr = blankToNull(dto.getContentTr());
        String en = blankToNull(dto.getContentEn());
        requireAtLeastOneContent(tr, en);
        validateContentLengths(tr, en);
        String visibility = normalizeVisibility(dto.getVisibility());

        // Both PERSONAL and SHARED templates may be tied to a product (or left global/null).
        Long productId = dto.getProductId();
        validateProductExists(productId);

        CannedResponse entity = CannedResponse.builder()
                .title(title)
                .shortcut(normalizeShortcut(dto.getShortcut()))
                .contentTr(tr)
                .contentEn(en)
                .scope(scope)
                .ownerAgentId(userId)
                .productId(productId)
                .visibility(visibility)
                .build();

        CannedResponse saved = repository.save(entity);
        log.info("Hazır yanıt oluşturuldu. ID: {}, Kapsam: {}, Sahip: {}", saved.getId(), scope, userId);
        return CannedResponseDTO.fromEntity(saved, false);
    }

    /**
     * Full update of an editable canned response. The caller must own the template (personal)
     * or be admin/manager (shared). Moving a template into or out of {@code SHARED} requires the
     * admin/manager role. The owner is preserved.
     *
     * @throws ResponseStatusException 404 (missing), 403 (not allowed), 400 (validation)
     */
    @Transactional
    public CannedResponseDTO update(Long id, CannedResponseDTO dto, String userId, List<String> roles) {
        CannedResponse existing = findOrThrow(id);
        ensureCanManage(existing, userId, roles);

        String targetScope = normalizeScope(dto.getScope());
        if (SCOPE_SHARED.equals(targetScope) || SCOPE_SHARED.equals(existing.getScope())) {
            requireAdmin(roles);
        }

        String title = requireValidTitle(dto.getTitle());
        String tr = blankToNull(dto.getContentTr());
        String en = blankToNull(dto.getContentEn());
        requireAtLeastOneContent(tr, en);
        validateContentLengths(tr, en);

        existing.setTitle(title);
        existing.setShortcut(normalizeShortcut(dto.getShortcut()));
        existing.setContentTr(tr);
        existing.setContentEn(en);
        existing.setScope(targetScope);
        existing.setVisibility(normalizeVisibility(dto.getVisibility()));
        // Either scope may carry an optional product binding (null = global).
        Long productId = dto.getProductId();
        validateProductExists(productId);
        existing.setProductId(productId);

        CannedResponse saved = repository.save(existing);
        log.info("Hazır yanıt güncellendi. ID: {}", saved.getId());
        boolean favorite = favoriteRepository.existsByUserIdAndCannedResponseId(userId, id);
        return CannedResponseDTO.fromEntity(saved, favorite);
    }

    /**
     * Deletes a canned response. The caller must own it (personal) or be admin/manager (shared).
     *
     * @throws ResponseStatusException 404 (missing), 403 (not allowed)
     */
    @Transactional
    public void delete(Long id, String userId, List<String> roles) {
        CannedResponse existing = findOrThrow(id);
        ensureCanManage(existing, userId, roles);
        repository.delete(existing);
        log.info("Hazır yanıt silindi. ID: {}", id);
    }

    // --------------------------------------------------------------------
    // Favorites
    // --------------------------------------------------------------------

    /**
     * Marks a template as a favorite for the user (idempotent). The template must be visible
     * to the user.
     *
     * @throws ResponseStatusException 404 (missing), 403 (not visible)
     */
    @Transactional
    public void addFavorite(Long id, String userId) {
        CannedResponse template = findOrThrow(id);
        ensureVisible(template, userId);
        if (!favoriteRepository.existsByUserIdAndCannedResponseId(userId, id)) {
            favoriteRepository.save(CannedResponseFavorite.builder()
                    .userId(userId)
                    .cannedResponseId(id)
                    .build());
            log.info("Hazır yanıt favorilendi. ID: {}, Kullanıcı: {}", id, userId);
        }
    }

    /**
     * Removes a favorite for the user (idempotent — no error if it was not favorited).
     */
    @Transactional
    public void removeFavorite(Long id, String userId) {
        favoriteRepository.deleteByUserIdAndCannedResponseId(userId, id);
        log.info("Hazır yanıt favorisi kaldırıldı. ID: {}, Kullanıcı: {}", id, userId);
    }

    // --------------------------------------------------------------------
    // Helpers
    // --------------------------------------------------------------------

    private CannedResponse findOrThrow(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "error.cannedResponse.not.found"));
    }

    /** Whether a {@code visibility} filter keeps the template ({@code BOTH} always suits). */
    private boolean matchesVisibility(CannedResponse c, String requested) {
        if (requested == null) return true;
        if (VISIBILITY_BOTH.equals(c.getVisibility())) return true;
        return requested.equals(c.getVisibility());
    }

    private boolean matchesSearch(CannedResponse c, String needle) {
        if (needle == null) return true;
        return containsIgnoreCase(c.getTitle(), needle)
                || containsIgnoreCase(c.getShortcut(), needle)
                || containsIgnoreCase(c.getContentTr(), needle)
                || containsIgnoreCase(c.getContentEn(), needle);
    }

    private boolean containsIgnoreCase(String haystack, String needle) {
        return haystack != null && haystack.toLowerCase().contains(needle);
    }

    /** Personal: only the owner manages. Shared: only admin/manager manages. */
    private void ensureCanManage(CannedResponse c, String userId, List<String> roles) {
        if (SCOPE_SHARED.equals(c.getScope())) {
            requireAdmin(roles);
            return;
        }
        if (!c.getOwnerAgentId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "error.cannedResponse.forbidden");
        }
    }

    /** A user may see their own personal templates and all shared templates. */
    private void ensureVisible(CannedResponse c, String userId) {
        boolean visible = SCOPE_SHARED.equals(c.getScope())
                || (SCOPE_PERSONAL.equals(c.getScope()) && c.getOwnerAgentId().equals(userId));
        if (!visible) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "error.cannedResponse.forbidden");
        }
    }

    private void requireAdmin(List<String> roles) {
        // Paylaşılan şablon yönetimi içerik yönetimidir: LEAD_AGENT veya ADMIN.
        // MANAGER (gözetim) içerik yönetmez.
        if (!(AuthRoles.isLeadAgent(roles) || AuthRoles.isAdmin(roles))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "error.cannedResponse.shared.forbidden");
        }
    }

    private String normalizeScope(String scope) {
        if (scope == null || scope.isBlank()) return SCOPE_PERSONAL;
        String upper = scope.trim().toUpperCase();
        if (!SCOPE_PERSONAL.equals(upper) && !SCOPE_SHARED.equals(upper)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "error.cannedResponse.scope.invalid");
        }
        return upper;
    }

    /** Optional scope filter for listing: blank/null → no filter; an unknown value → 400. */
    private String validateScopeFilter(String scope) {
        if (scope == null || scope.isBlank()) return null;
        String upper = scope.trim().toUpperCase();
        if (!SCOPE_PERSONAL.equals(upper) && !SCOPE_SHARED.equals(upper)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "error.cannedResponse.scope.invalid");
        }
        return upper;
    }

    /** Optional visibility filter for listing: blank/null → no filter; an unknown value → 400. */
    private String validateVisibilityFilter(String visibility) {
        if (visibility == null || visibility.isBlank()) return null;
        String upper = visibility.trim().toUpperCase();
        if (!VISIBILITY_EXTERNAL.equals(upper) && !VISIBILITY_INTERNAL.equals(upper) && !VISIBILITY_BOTH.equals(upper)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "error.cannedResponse.visibility.invalid");
        }
        return upper;
    }

    private String normalizeVisibility(String visibility) {
        if (visibility == null || visibility.isBlank()) return VISIBILITY_BOTH;
        String upper = visibility.trim().toUpperCase();
        if (!VISIBILITY_EXTERNAL.equals(upper) && !VISIBILITY_INTERNAL.equals(upper) && !VISIBILITY_BOTH.equals(upper)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "error.cannedResponse.visibility.invalid");
        }
        return upper;
    }

    /** Trim, drop leading slashes and lower-case the shortcut; blank becomes {@code null}. */
    private String normalizeShortcut(String shortcut) {
        if (shortcut == null) return null;
        String cleaned = shortcut.trim().toLowerCase();
        while (cleaned.startsWith("/")) {
            cleaned = cleaned.substring(1).trim();
        }
        if (cleaned.isEmpty()) return null;
        if (cleaned.length() > SHORTCUT_MAX) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "error.cannedResponse.shortcut.too-long");
        }
        return cleaned;
    }

    private String requireValidTitle(String title) {
        if (title == null || title.trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "error.cannedResponse.title.empty");
        }
        String trimmed = title.trim();
        if (trimmed.length() > TITLE_MAX) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "error.cannedResponse.title.too-long");
        }
        return trimmed;
    }

    private void requireAtLeastOneContent(String tr, String en) {
        if (tr == null && en == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "error.cannedResponse.content.empty");
        }
    }

    private void validateContentLengths(String tr, String en) {
        if ((tr != null && tr.length() > CONTENT_MAX) || (en != null && en.length() > CONTENT_MAX)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "error.cannedResponse.content.too-long");
        }
    }

    private void validateProductExists(Long productId) {
        if (productId != null && !productRepository.existsById(productId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "error.product.not.found");
        }
    }

    private String blankToNull(String s) {
        if (s == null) return null;
        String trimmed = s.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
