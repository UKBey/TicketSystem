package com.ticketsystem.it_service_backend.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.ZonedDateTime;

/**
 * A comment on a {@link Ticket}.
 *
 * <p>{@code type} can be {@code INTERNAL} (visible only to agents/admins) or
 * {@code EXTERNAL} (also visible to the customer); if the type is missing,
 * {@code @PrePersist} defaults it to EXTERNAL. {@code authorId} is the Keycloak UUID
 * directly (not an FK) and corresponds to {@link User#id}.
 */
@Entity
@Table(name = "ticket_comments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Comment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ticket_id", nullable = false)
    private Ticket ticket;

    @Column(name = "author_id", nullable = false, length = 36)
    private String authorId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String message;

    /** INTERNAL (visible only to agents/admins) or EXTERNAL (also visible to the customer). */
    @Column(nullable = false, length = 10)
    private String type; // INTERNAL veya EXTERNAL

    @Column(name = "created_at", updatable = false)
    private ZonedDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = ZonedDateTime.now();
        if (this.type == null) {
            this.type = "EXTERNAL";
        }
    }
}
