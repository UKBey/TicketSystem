package com.ticketsystem.it_service_backend.repository;

import com.ticketsystem.it_service_backend.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

/**
 * JPA repository for {@link User} — provides role/email lookups and a native,
 * filtered, paged listing for the admin panel (the PK is the Keycloak UUID String).
 */
public interface UserRepository extends JpaRepository<User, String> {
    List<User> findByRole(String role);

    Optional<User> findByEmailIgnoreCase(String email);

    /**
     * Returns users with the given role who are authorized on the given product.
     * DISTINCT join over the {@code user_products} bridge — a user with multiple
     * product authorizations is still returned as a single row.
     */
    @Query("""
            SELECT DISTINCT u
            FROM User u
            JOIN u.authorizedProducts p
            WHERE u.role = :role
                AND p.id = :productId
            """)
    List<User> findByRoleAndAuthorizedProductsId(@Param("role") String role,
                                                   @Param("productId") Long productId);

    /**
     * Filtered + paged user list for the admin panel.
     * search: case-insensitive substring search over fullName or email (null disables the filter)
     * roles:  role filter list; when the filter is off, pass {@code roleFilterActive=false}
     * excludeGlobalRoles: when {@code true}, hides users whose (highest/display) role is ADMIN or
     *   MANAGER — used by the product-access panel, where global roles have all-product access and
     *   thus nothing to manage. A user holding ADMIN/MANAGER always has that as its primary role
     *   (see {@code resolveHighestRole}), so filtering the primary column suffices.
     */
    @Query(value = """
            SELECT * FROM users u
            WHERE (:roleFilterActive = FALSE OR u.role IN (:roles))
              AND (:excludeGlobalRoles = FALSE OR u.role IS NULL OR u.role NOT IN ('ADMIN', 'MANAGER'))
              AND (:productFilterActive = FALSE OR EXISTS (
                     SELECT 1 FROM user_products up WHERE up.user_id = u.id AND up.product_id IN (:productIds)))
              AND (CAST(:search AS text) IS NULL
                   OR LOWER(u.full_name) LIKE LOWER(CONCAT('%', CAST(:search AS text), '%'))
                   OR LOWER(u.email)     LIKE LOWER(CONCAT('%', CAST(:search AS text), '%')))
            """,
            countQuery = """
            SELECT COUNT(*) FROM users u
            WHERE (:roleFilterActive = FALSE OR u.role IN (:roles))
              AND (:excludeGlobalRoles = FALSE OR u.role IS NULL OR u.role NOT IN ('ADMIN', 'MANAGER'))
              AND (:productFilterActive = FALSE OR EXISTS (
                     SELECT 1 FROM user_products up WHERE up.user_id = u.id AND up.product_id IN (:productIds)))
              AND (CAST(:search AS text) IS NULL
                   OR LOWER(u.full_name) LIKE LOWER(CONCAT('%', CAST(:search AS text), '%'))
                   OR LOWER(u.email)     LIKE LOWER(CONCAT('%', CAST(:search AS text), '%')))
            """,
            nativeQuery = true)
    Page<User> findFiltered(@Param("roleFilterActive") Boolean roleFilterActive,
                            @Param("roles")            List<String> roles,
                            @Param("search")           String search,
                            @Param("excludeGlobalRoles") Boolean excludeGlobalRoles,
                            @Param("productFilterActive") Boolean productFilterActive,
                            @Param("productIds")       List<Long> productIds,
                            Pageable pageable);
}
