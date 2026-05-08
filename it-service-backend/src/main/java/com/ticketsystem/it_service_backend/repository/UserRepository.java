package com.ticketsystem.it_service_backend.repository;

import com.ticketsystem.it_service_backend.entity.User;
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
}
