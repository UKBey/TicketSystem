package com.ticketsystem.it_service_backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.ZonedDateTime;

/**
 * Single-use password-reset token — ManyToOne to {@link User}.
 *
 * <p>The plaintext token only travels in the email sent to the user; the DB stores
 * only its SHA-256 hash, so a DB leak does not compromise active tokens.
 * {@code usedAt} is stamped when the token is consumed; the same token cannot be
 * used twice.
 */
@Entity
@Table(name = "password_reset_tokens")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PasswordResetToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /**
     * SHA-256 hash of the token. The plaintext token only travels in the email;
     * the DB stores only the hash, so even a DB leak does not yield a usable token.
     */
    @Column(name = "token_hash", nullable = false, unique = true, length = 255)
    private String tokenHash;

    @Column(name = "created_at", nullable = false, updatable = false)
    private ZonedDateTime createdAt;

    @Column(name = "expires_at", nullable = false)
    private ZonedDateTime expiresAt;

    /**
     * Marked once the token has been consumed; each token may be used only once.
     */
    @Column(name = "used_at")
    private ZonedDateTime usedAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = ZonedDateTime.now();
        }
    }

    public boolean isUsed() {
        return usedAt != null;
    }

    public boolean isExpired() {
        return expiresAt.isBefore(ZonedDateTime.now());
    }

    public boolean isValid() {
        return !isUsed() && !isExpired();
    }
}
