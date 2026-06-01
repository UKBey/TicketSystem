package com.ticketsystem.it_service_backend.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.Objects;

/**
 * A user's ⭐ favorite on a {@link CannedResponse}. The composite primary key
 * {@code (userId, cannedResponseId)} guarantees a user can favorite a template only once.
 * Rows are removed automatically (DB {@code ON DELETE CASCADE}) when the template is deleted.
 */
@Entity
@Table(name = "canned_response_favorites")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@IdClass(CannedResponseFavorite.FavoriteId.class)
public class CannedResponseFavorite {

    @Id
    @Column(name = "user_id", length = 50)
    private String userId;

    @Id
    @Column(name = "canned_response_id")
    private Long cannedResponseId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    /** Composite key for {@link CannedResponseFavorite}. */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FavoriteId implements Serializable {
        private String userId;
        private Long cannedResponseId;

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof FavoriteId that)) return false;
            return Objects.equals(userId, that.userId)
                    && Objects.equals(cannedResponseId, that.cannedResponseId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(userId, cannedResponseId);
        }
    }
}
