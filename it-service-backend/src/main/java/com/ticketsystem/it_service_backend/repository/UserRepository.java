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
 * {@link User} için JPA repository — rol/e-posta lookup'ları ve admin paneli için
 * native filtreli sayfalı listeleme sağlar (PK Keycloak UUID String).
 */
public interface UserRepository extends JpaRepository<User, String> {
    List<User> findByRole(String role);

    Optional<User> findByEmailIgnoreCase(String email);

    /**
     * Belirli ürüne yetkili ve verilen role sahip kullanıcıları döner.
     * {@code user_products} köprüsü üzerinden DISTINCT join — aynı kullanıcının
     * birden fazla ürün yetkisi varsa tek satır olarak gelir.
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
     * Admin panel için filtreli + sayfalı kullanıcı listesi.
     * search: fullName veya email içinde case-insensitive arama (null ise filtre uygulanmaz)
     * roles:  rol filtre listesi; filtre kapalıyken `roleFilterActive=false` ile gönderilir
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
