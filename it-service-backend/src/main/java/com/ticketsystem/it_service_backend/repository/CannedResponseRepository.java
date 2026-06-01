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
 * {@code SHARED} template. The two query variants differ only in how the optional product
 * filter narrows the {@code SHARED} set:
 * <ul>
 *   <li>{@link #findVisibleForProduct} — composer picker scoped to a ticket: global shared
 *       ({@code productId IS NULL}) plus the ticket product's shared templates.</li>
 *   <li>{@link #findVisibleToUser} — management screen / unscoped: every shared template,
 *       regardless of product.</li>
 * </ul>
 * Newest-updated first so "recently touched" rises to the top.
 */
public interface CannedResponseRepository extends JpaRepository<CannedResponse, Long> {

    @Query("""
            SELECT c FROM CannedResponse c
            WHERE (c.scope = 'PERSONAL' AND c.ownerAgentId = :userId)
               OR (c.scope = 'SHARED' AND (c.productId IS NULL OR c.productId = :productId))
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
