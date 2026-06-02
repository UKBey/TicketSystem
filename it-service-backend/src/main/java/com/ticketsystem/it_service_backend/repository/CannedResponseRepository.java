package com.ticketsystem.it_service_backend.repository;

import com.ticketsystem.it_service_backend.entity.CannedResponse;
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
}
