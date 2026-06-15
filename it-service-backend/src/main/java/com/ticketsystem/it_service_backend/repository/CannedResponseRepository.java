package com.ticketsystem.it_service_backend.repository;

import com.ticketsystem.it_service_backend.entity.CannedResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * JPA repository for {@link CannedResponse}.
 *
 * <p>Visibility for a given agent is always: their own {@code PERSONAL} templates plus every
 * {@code SHARED} template. The two query variants differ in whether the optional product
 * binding narrows the result:
 * <ul>
 *   <li>{@link #findVisibleForProduct} — composer picker scoped to a ticket: only templates
 *       that are global ({@code productId IS NULL}) or bound to the ticket's product (applies
 *       to both personal and shared templates).</li>
 *   <li>{@link #findVisibleToUser} — management screen / unscoped: every visible template,
 *       regardless of product (the page filters by product client-side).</li>
 * </ul>
 * Newest-updated first so "recently touched" rises to the top.
 */
public interface CannedResponseRepository extends JpaRepository<CannedResponse, Long> {

    @Query("""
            SELECT c FROM CannedResponse c
            WHERE (c.productId IS NULL OR c.productId = :productId)
              AND ((c.scope = 'PERSONAL' AND c.ownerAgentId = :userId)
                   OR c.scope = 'SHARED')
            ORDER BY c.updatedAt DESC
            """)
    List<CannedResponse> findVisibleForProduct(@Param("userId") String userId,
                                               @Param("productId") Long productId);

    @Query("""
            SELECT c FROM CannedResponse c
            WHERE (c.scope = 'PERSONAL' AND c.ownerAgentId = :userId)
               OR c.scope = 'SHARED'
            ORDER BY c.updatedAt DESC
            """)
    List<CannedResponse> findVisibleToUser(@Param("userId") String userId);

    /**
     * Paginated + filtered listing for the management screen. Pushes every filter that the
     * page previously applied client-side into SQL (exact-match semantics, not the composer's
     * "BOTH always matches"):
     * <ul>
     *   <li>{@code scope} / {@code visibility}: exact when non-null.</li>
     *   <li>{@code productMode}: {@code ALL} (any), {@code GLOBAL} (productId IS NULL),
     *       {@code PRODUCT} (productId = :productId).</li>
     *   <li>{@code lang}: {@code tr}/{@code en} keeps rows that have that content variant.</li>
     *   <li>{@code q}: already lower-cased {@code %needle%} matched against title/shortcut/contents.</li>
     * </ul>
     * Visibility base is unchanged: the user's own PERSONAL plus all SHARED.
     *
     * <p>Ordering is owned entirely by this query (callers must pass an <em>unsorted</em>
     * {@link Pageable}): the user's favorites float to the top, then newest-updated first within
     * each group. Favorite state is per-user, so it's an {@code EXISTS} subquery on the
     * {@code canned_response_favorites} table (avoids the row multiplication a {@code LEFT JOIN}
     * would cause in the paged count query).
     */
    @Query("""
            SELECT c FROM CannedResponse c
            WHERE ((c.scope = 'PERSONAL' AND c.ownerAgentId = :userId) OR c.scope = 'SHARED')
              AND (:scope IS NULL OR cast(c.scope as String) = :scope)
              AND (:visibility IS NULL OR cast(c.visibility as String) = :visibility)
              AND ( :productMode = 'ALL'
                    OR (:productMode = 'GLOBAL' AND c.productId IS NULL)
                    OR (:productMode = 'PRODUCT' AND c.productId = :productId) )
              AND ( :lang IS NULL
                    OR (:lang = 'tr' AND c.contentTr IS NOT NULL AND c.contentTr <> '')
                    OR (:lang = 'en' AND c.contentEn IS NOT NULL AND c.contentEn <> '') )
              AND ( :q IS NULL
                    OR LOWER(c.title) LIKE :q
                    OR LOWER(c.shortcut) LIKE :q
                    OR LOWER(c.contentTr) LIKE :q
                    OR LOWER(c.contentEn) LIKE :q )
            ORDER BY CASE WHEN EXISTS (
                         SELECT 1 FROM CannedResponseFavorite f
                         WHERE f.cannedResponseId = c.id AND f.userId = :userId
                     ) THEN 0 ELSE 1 END,
                     c.updatedAt DESC
            """)
    Page<CannedResponse> findVisiblePaged(@Param("userId") String userId,
                                          @Param("scope") String scope,
                                          @Param("visibility") String visibility,
                                          @Param("productMode") String productMode,
                                          @Param("productId") Long productId,
                                          @Param("lang") String lang,
                                          @Param("q") String q,
                                          Pageable pageable);
}
