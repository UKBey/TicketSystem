package com.ticketsystem.it_service_backend.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.ZonedDateTime;

/**
 * Bir {@link Ticket} altındaki yorum.
 *
 * <p>{@code type = INTERNAL} (yalnızca agent/admin görür) veya {@code EXTERNAL}
 * (müşteriye de görünür) olabilir; tip eksikse {@code @PrePersist} EXTERNAL atar.
 * {@code authorId} doğrudan Keycloak UUID'sidir (FK değil), {@link User#id}'ye karşılık gelir.
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

    /** INTERNAL (yalnız agent/admin görür) veya EXTERNAL (müşteriye de görünür). */
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
