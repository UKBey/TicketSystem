package com.ticketsystem.it_service_backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.ZonedDateTime;

/**
 * Şifre sıfırlama tek-kullanımlık token'ı — {@link User} ile ManyToOne.
 *
 * <p>Plaintext token sadece kullanıcıya gönderilen mail içinde gezer; DB'de yalnızca
 * SHA-256 hash'i tutulur, böylece DB sızıntısı token'ları geçersiz kılmaz.
 * {@code usedAt} alanı token tüketildiğinde damgalanır; aynı token ikinci kez kullanılamaz.
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
     * Token'ın SHA-256 hash'i. Plaintext token sadece mail içinde uçar; DB'de
     * yalnızca hash tutulur, böylece DB sızsa bile token kullanılamaz.
     */
    @Column(name = "token_hash", nullable = false, unique = true, length = 255)
    private String tokenHash;

    @Column(name = "created_at", nullable = false, updatable = false)
    private ZonedDateTime createdAt;

    @Column(name = "expires_at", nullable = false)
    private ZonedDateTime expiresAt;

    /**
     * Token tüketildiğinde işaretlenir; bir token sadece bir kez kullanılabilir.
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
