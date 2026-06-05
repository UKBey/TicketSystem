package com.ticketsystem.it_service_backend.entity;

import org.junit.jupiter.api.Test;

import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class PasswordResetTokenTest {

    private PasswordResetToken.PasswordResetTokenBuilder base() {
        return PasswordResetToken.builder()
                .id(1L)
                .user(User.builder().id("u-1").build())
                .tokenHash("hash")
                .expiresAt(ZonedDateTime.now().plusHours(1));
    }

    @Test
    void freshToken_isValid() {
        PasswordResetToken t = base().build();

        assertThat(t.isUsed()).isFalse();
        assertThat(t.isExpired()).isFalse();
        assertThat(t.isValid()).isTrue();
    }

    @Test
    void usedToken_isNotValid() {
        PasswordResetToken t = base().usedAt(ZonedDateTime.now()).build();

        assertThat(t.isUsed()).isTrue();
        assertThat(t.isValid()).isFalse();
    }

    @Test
    void expiredToken_isNotValid() {
        PasswordResetToken t = base().expiresAt(ZonedDateTime.now().minusMinutes(1)).build();

        assertThat(t.isExpired()).isTrue();
        assertThat(t.isValid()).isFalse();
    }

    @Test
    void onCreate_setsCreatedAtWhenMissing() {
        PasswordResetToken t = base().build();
        t.onCreate();
        assertThat(t.getCreatedAt()).isNotNull();
    }

    @Test
    void onCreate_keepsExistingCreatedAt() {
        ZonedDateTime fixed = ZonedDateTime.now().minusDays(2);
        PasswordResetToken t = base().createdAt(fixed).build();
        t.onCreate();
        assertThat(t.getCreatedAt()).isEqualTo(fixed);
    }
}
