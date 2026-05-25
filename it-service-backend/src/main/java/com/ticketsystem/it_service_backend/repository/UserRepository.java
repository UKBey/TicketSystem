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
     */
    @Query(value = """
            SELECT * FROM users u
            WHERE (:roleFilterActive = FALSE OR u.role IN (:roles))
              AND (CAST(:search AS text) IS NULL
                   OR LOWER(u.full_name) LIKE LOWER(CONCAT('%', CAST(:search AS text), '%'))
                   OR LOWER(u.email)     LIKE LOWER(CONCAT('%', CAST(:search AS text), '%')))
            ORDER BY u.full_name
            """,
            countQuery = """
            SELECT COUNT(*) FROM users u
            WHERE (:roleFilterActive = FALSE OR u.role IN (:roles))
              AND (CAST(:search AS text) IS NULL
                   OR LOWER(u.full_name) LIKE LOWER(CONCAT('%', CAST(:search AS text), '%'))
                   OR LOWER(u.email)     LIKE LOWER(CONCAT('%', CAST(:search AS text), '%')))
            """,
            nativeQuery = true)
    Page<User> findFiltered(@Param("roleFilterActive") Boolean roleFilterActive,
                            @Param("roles")            List<String> roles,
                            @Param("search")           String search,
                            Pageable pageable);
}
