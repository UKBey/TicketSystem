package com.ticketsystem.it_service_backend.repository;

import com.ticketsystem.it_service_backend.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface UserRepository extends JpaRepository<User, String> {
    List<User> findByRole(String role);
    
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
     * role:   tam eşleşme (null ise filtre uygulanmaz)
     */
    @Query(value = """
            SELECT * FROM users u
            WHERE u.role != 'MANAGER'
              AND (CAST(:role AS text) IS NULL OR u.role = CAST(:role AS text))
              AND (CAST(:search AS text) IS NULL
                   OR LOWER(u.full_name) LIKE LOWER(CONCAT('%', CAST(:search AS text), '%'))
                   OR LOWER(u.email)     LIKE LOWER(CONCAT('%', CAST(:search AS text), '%')))
            ORDER BY u.full_name
            """,
            countQuery = """
            SELECT COUNT(*) FROM users u
            WHERE u.role != 'MANAGER'
              AND (CAST(:role AS text) IS NULL OR u.role = CAST(:role AS text))
              AND (CAST(:search AS text) IS NULL
                   OR LOWER(u.full_name) LIKE LOWER(CONCAT('%', CAST(:search AS text), '%'))
                   OR LOWER(u.email)     LIKE LOWER(CONCAT('%', CAST(:search AS text), '%')))
            """,
            nativeQuery = true)
    Page<User> findFiltered(@Param("role")   String role,
                            @Param("search") String search,
                            Pageable pageable);
}
