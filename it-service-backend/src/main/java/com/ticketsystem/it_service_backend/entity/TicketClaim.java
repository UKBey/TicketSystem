package com.ticketsystem.it_service_backend.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * Bir agent'ın bir {@link Ticket}'ı sahiplenme kaydı.
 *
 * <p>{@link Ticket} ⇄ agent ilişkisi many-to-many olarak bu köprü tablosunda tutulur —
 * yani aynı bilete birden fazla agent claim atabilir. {@code (ticket_id, agent_id)}
 * unique constraint'i ile aynı agent aynı bileti iki kez sahiplenemez.
 */
@Entity
@Table(name = "ticket_claims",
       uniqueConstraints = @UniqueConstraint(columnNames = {"ticket_id", "agent_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TicketClaim {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ticket_id", nullable = false)
    private Ticket ticket;

    /** Sahiplenen agent'ın Keycloak UUID'si — {@link User#id}'ye karşılık gelir. */
    @Column(name = "agent_id", nullable = false, length = 255)
    private String agentId;

    @Column(name = "claimed_at", nullable = false)
    private LocalDateTime claimedAt;

    @PrePersist
    protected void onCreate() {
        if (this.claimedAt == null) {
            this.claimedAt = LocalDateTime.now();
        }
    }
}
