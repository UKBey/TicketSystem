package com.ticketsystem.it_service_backend.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * Record of an agent claiming a {@link Ticket}.
 *
 * <p>The {@link Ticket} ⇄ agent relationship is modeled many-to-many through this
 * bridge table — multiple agents can claim the same ticket. The
 * {@code (ticket_id, agent_id)} unique constraint prevents the same agent from
 * claiming the same ticket twice.
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

    /** Keycloak UUID of the claiming agent — corresponds to {@link User#id}. */
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
