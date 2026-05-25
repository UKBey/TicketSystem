package com.ticketsystem.it_service_backend.repository;

import com.ticketsystem.it_service_backend.entity.PasswordResetToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.ZonedDateTime;
import java.util.Optional;

/**
 * JPA repository for {@link PasswordResetToken} — hashed-token lookup, plus bulk
 * invalidation of a user's outstanding tokens when a new reset request arrives.
 */
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    Optional<PasswordResetToken> findByTokenHash(String tokenHash);

    /**
     * Invalidates the user's currently valid (unused + unexpired) tokens.
     * Called when a new reset request arrives, to deactivate the previous links.
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
