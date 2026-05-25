package com.ticketsystem.it_service_backend.repository;

import com.ticketsystem.it_service_backend.entity.PasswordResetToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.ZonedDateTime;
import java.util.Optional;

/**
 * {@link PasswordResetToken} için JPA repository — hash'lenmiş token lookup ve
 * yeni reset isteği geldiğinde kullanıcının açık token'larını toplu iptal etme.
 */
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    Optional<PasswordResetToken> findByTokenHash(String tokenHash);

    /**
     * Kullanıcının halen geçerli (kullanılmamış + süresi dolmamış) token'larını
     * iptal eder. Yeni reset isteği geldiğinde önceki linkleri devre dışı bırakmak için.
     */
    @Modifying
    @Query("""
            UPDATE PasswordResetToken t
            SET t.usedAt = :now
            WHERE t.user.id = :userId
              AND t.usedAt IS NULL
              AND t.expiresAt > :now
            """)
    int invalidateActiveTokensForUser(@Param("userId") String userId, @Param("now") ZonedDateTime now);
}
